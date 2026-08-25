package com.acomi.acomi_backend.auth.application.service;

import com.acomi.acomi_backend.auth.api.dto.request.LoginRequest;
import com.acomi.acomi_backend.auth.api.dto.request.OtpVerifiedActionRequest;
import com.acomi.acomi_backend.auth.api.dto.request.PasswordAccountDeletionRequest;
import com.acomi.acomi_backend.auth.api.dto.request.RegisterRequest;
import com.acomi.acomi_backend.auth.api.dto.request.ResetPasswordRequest;
import com.acomi.acomi_backend.auth.api.dto.request.SendOtpRequest;
import com.acomi.acomi_backend.auth.api.dto.request.VerifyOtpRequest;
import com.acomi.acomi_backend.auth.api.dto.response.AuthTokenResponse;
import com.acomi.acomi_backend.auth.api.dto.response.SendOtpResponse;
import com.acomi.acomi_backend.auth.api.dto.response.VerifyOtpResponse;
import com.acomi.acomi_backend.auth.application.otp.OtpDispatchResult;
import com.acomi.acomi_backend.auth.application.otp.RegistrationVerification;
import com.acomi.acomi_backend.auth.domain.model.OtpPurpose;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.common.util.MobileNumberNormalizer;
import com.acomi.acomi_backend.config.security.JwtService;
import com.acomi.acomi_backend.config.security.UserPrincipal;
import com.acomi.acomi_backend.member.domain.model.MemberDocumentType;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberDocumentEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberDocumentRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.acomi.acomi_backend.user.api.dto.request.CompleteUserProfileRequest;
import com.acomi.acomi_backend.user.api.dto.request.UpdateUserRequest;
import com.acomi.acomi_backend.user.api.dto.response.UserResponse;
import com.acomi.acomi_backend.user.domain.model.KycStatus;
import com.acomi.acomi_backend.user.domain.model.ProfileStatus;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String PENDING_UPLOAD_FILE_URL = "pending-upload";
    private static final int MAX_DOCUMENT_FILE_URL_LENGTH = 1024;

    private final OtpService otpService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final MemberRepository memberRepository;
    private final MemberDocumentRepository memberDocumentRepository;
    private final AccountDeletionService accountDeletionService;
    private final PasswordEncoder passwordEncoder;

    private static final String INVALID_CREDENTIALS = "Invalid mobile number or password.";
    /** Valid unused bcrypt hash so missing users still take a password-check path. */
    private static final String DUMMY_PASSWORD_HASH =
            "{bcrypt}$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG";

    @Transactional
    public AuthTokenResponse register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match");
        }

        String mobileNumber = MobileNumberNormalizer.normalize(request.getMobileNumber());
        if (userRepository.findByMobileNumberAndIsActiveTrue(mobileNumber).isPresent()) {
            throw new BusinessException("This mobile number is already registered.", HttpStatus.CONFLICT);
        }

        if (!StringUtils.hasText(request.getVerificationToken())) {
            throw new BusinessException("Mobile number verification is required.");
        }
        otpService.consumeRegistrationVerificationToken(mobileNumber, request.getVerificationToken());

        LocalDateTime now = LocalDateTime.now();
        UserEntity user;
        try {
            user = userRepository.save(UserEntity.builder()
                    .mobileNumber(mobileNumber)
                    .fullName(request.getFullName().trim())
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    .mobileVerifiedAt(now)
                    .build());
            userRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("This mobile number is already registered.", HttpStatus.CONFLICT);
        }

        return toAuthTokenResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthTokenResponse login(LoginRequest request) {
        String mobileNumber = MobileNumberNormalizer.normalize(request.getMobileNumber());
        UserEntity user = userRepository.findByMobileNumberAndIsActiveTrue(mobileNumber).orElse(null);
        if (!passwordMatches(request.getPassword(), user)) {
            throw new BusinessException(INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);
        }
        return toAuthTokenResponse(user);
    }

    @Transactional
    public AuthTokenResponse loginWithOtp(OtpVerifiedActionRequest request) {
        String mobileNumber = MobileNumberNormalizer.normalize(request.getMobileNumber());
        otpService.consumeVerificationToken(mobileNumber, request.getVerificationToken(), OtpPurpose.LOGIN);
        UserEntity user = userRepository.findByMobileNumberAndIsActiveTrue(mobileNumber).orElse(null);
        if (user == null) {
            throw new BusinessException(INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);
        }
        return toAuthTokenResponse(user);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match");
        }
        String mobileNumber = MobileNumberNormalizer.normalize(request.getMobileNumber());
        otpService.consumeVerificationToken(
                mobileNumber, request.getVerificationToken(), OtpPurpose.RESET_PASSWORD);
        UserEntity user = userRepository.findByMobileNumberAndIsActiveTrue(mobileNumber)
                .orElseThrow(() -> new BusinessException(OtpService.INVALID_VERIFICATION_TOKEN_MESSAGE));
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);
    }

    public SendOtpResponse sendOtp(SendOtpRequest request, String requestIp) {
        String mobileNumber = MobileNumberNormalizer.normalize(request.getMobileNumber());
        OtpPurpose purpose = request.getPurpose();
        if (purpose == null) {
            throw new BusinessException("OTP purpose is required");
        }
        boolean accountExists = userRepository.findByMobileNumberAndIsActiveTrue(mobileNumber).isPresent();
        if (purpose == OtpPurpose.REGISTER && accountExists) {
            throw new BusinessException("This mobile number is already registered.", HttpStatus.CONFLICT);
        }
        boolean dispatch = shouldDispatchOtp(purpose, accountExists);
        OtpDispatchResult dispatchResult = otpService.sendOtp(mobileNumber, purpose, requestIp, dispatch);

        return SendOtpResponse.builder()
                .mobileNumber(mobileNumber)
                .purpose(purpose)
                .expiresIn(dispatchResult.expiresInSeconds())
                .resendAfter(dispatchResult.resendAfterSeconds())
                .message("OTP sent successfully")
                .build();
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public VerifyOtpResponse verifyOtp(VerifyOtpRequest request) {
        if (request.getPurpose() == null) {
            throw new BusinessException("OTP purpose is required");
        }
        String mobileNumber = MobileNumberNormalizer.normalize(request.getMobileNumber());
        rejectRegisterOtpForActiveMobile(mobileNumber, request.getPurpose());
        RegistrationVerification verification =
                otpService.verifyAndIssueToken(mobileNumber, request.getOtp(), request.getPurpose());

        return VerifyOtpResponse.builder()
                .verified(true)
                .verificationToken(verification.verificationToken())
                .expiresIn(verification.expiresInSeconds())
                .build();
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        UserEntity user = loadCurrentUserEntity();
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateCurrentUser(UpdateUserRequest request) {
        UserEntity user = loadCurrentUserEntity();
        user.setFullName(request.getFullName().trim());
        UserEntity saved = userRepository.save(user);
        syncLinkedMemberNames(saved);
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse completeCurrentUserProfile(CompleteUserProfileRequest request) {
        UserEntity user = loadCurrentUserEntity();
        applyProfileFields(user, request);

        int documentsUploaded = countRequestedDocuments(request);
        user.setDocumentsUploaded(documentsUploaded);
        user.setProfileCompletionPercentage(calculateCompletionPercentage(user, documentsUploaded));
        user.setProfileCompleted(true);
        user.setProfileStatus(
                request.getProfileStatus() != null ? request.getProfileStatus() : ProfileStatus.COMPLETED);
        user.setProfileCompletedAt(LocalDateTime.now());
        if (documentsUploaded > 0) {
            user.setKycStatus(KycStatus.PENDING);
        }

        UserEntity saved = userRepository.save(user);
        syncLinkedMembersFromProfile(saved, request);
        return UserResponse.from(saved);
    }

    /**
     * Deletes the authenticated user's account and associated personal data.
     * Identity is taken only from the JWT principal — the client cannot supply a userId.
     */
    @Transactional
    public void deleteCurrentAccount() {
        accountDeletionService.deleteAuthenticatedAccount();
    }

    @Transactional
    public void deleteAccountByOtp(OtpVerifiedActionRequest request) {
        accountDeletionService.deleteAccountByOtp(request);
    }

    @Transactional
    public void deleteAccountByPassword(PasswordAccountDeletionRequest request) {
        accountDeletionService.deleteAccountByPassword(request);
    }

    private void applyProfileFields(UserEntity user, CompleteUserProfileRequest request) {
        user.setFullName(request.getFullName().trim());
        user.setGender(request.getGender());
        user.setDateOfBirth(parseDateOfBirth(request.getDateOfBirth()));
        user.setEmail(trimToNull(request.getEmail()));
        user.setProfilePhotoUrl(trimToNull(request.getProfilePhotoUrl()));
        user.setPermanentAddress(request.getPermanentAddress().trim());
        user.setCity(request.getCity().trim());
        user.setState(request.getState().trim());
        user.setPincode(request.getPincode().trim());
    }

    private void syncLinkedMemberNames(UserEntity user) {
        for (MemberEntity member : memberRepository.findActiveByUserId(user.getId())) {
            member.setFullName(user.getFullName());
            memberRepository.save(member);
        }
    }

    private void syncLinkedMembersFromProfile(UserEntity user, CompleteUserProfileRequest request) {
        for (MemberEntity member : memberRepository.findActiveByUserId(user.getId())) {
            member.setFullName(user.getFullName());
            member.setGender(user.getGender());

            if (hasEmergencyContact(request)) {
                member.setEmergencyContactName(trimToNull(request.getEmergencyContactName()));
                member.setEmergencyContactMobile(trimToNull(request.getEmergencyContactMobile()));
                member.setEmergencyContactRelation(trimToNull(request.getEmergencyContactRelation()));
            }

            memberRepository.save(member);
            saveMemberDocuments(member, request);
        }
    }

    private void saveMemberDocuments(MemberEntity member, CompleteUserProfileRequest request) {
        List<DocumentUpload> uploads = buildDocumentUploads(request);
        LocalDateTime now = LocalDateTime.now();
        List<MemberDocumentEntity> existing =
                memberDocumentRepository.findByMemberIdOrderByUploadedAtDesc(member.getId());

        for (DocumentUpload upload : uploads) {
            Optional<MemberDocumentEntity> matched = existing.stream()
                    .filter(document -> matchesDocumentUpload(document, upload))
                    .findFirst();

            if (matched.isPresent()) {
                MemberDocumentEntity document = matched.get();
                document.setDocumentNumber(upload.number());
                document.setFileUrl(upload.fileUrl());
                document.setUploadedAt(now);
                memberDocumentRepository.save(document);
                continue;
            }

            memberDocumentRepository.save(MemberDocumentEntity.builder()
                    .member(member)
                    .documentType(upload.type())
                    .documentNumber(upload.number())
                    .fileUrl(upload.fileUrl())
                    .uploadedAt(now)
                    .build());
        }
    }

    private boolean matchesDocumentUpload(MemberDocumentEntity document, DocumentUpload upload) {
        if (upload.type() != MemberDocumentType.OTHER) {
            return document.getDocumentType() == upload.type();
        }
        return document.getDocumentType() == MemberDocumentType.OTHER
                && Objects.equals(document.getDocumentNumber(), upload.number());
    }

    private List<DocumentUpload> buildDocumentUploads(CompleteUserProfileRequest request) {
        List<DocumentUpload> uploads = new ArrayList<>();

        String identityFileUrl = resolveDocumentFileUrl(request.getIdentityProofFileUrl());
        if (request.getIdentityDocumentType() != null
                && (StringUtils.hasText(request.getIdentityDocumentNumber()) || identityFileUrl != null)) {
            uploads.add(new DocumentUpload(
                    request.getIdentityDocumentType(),
                    StringUtils.hasText(request.getIdentityDocumentNumber())
                            ? request.getIdentityDocumentNumber().trim()
                            : "Identity document",
                    identityFileUrl != null ? identityFileUrl : PENDING_UPLOAD_FILE_URL));
        } else if (identityFileUrl != null) {
            uploads.add(new DocumentUpload(
                    MemberDocumentType.OTHER, "Identity proof", identityFileUrl));
        }

        String addressFileUrl = resolveDocumentFileUrl(request.getAddressProofFileUrl());
        if (addressFileUrl != null) {
            uploads.add(new DocumentUpload(
                    MemberDocumentType.OTHER, "Address proof", addressFileUrl));
        }

        String additionalFileUrl = resolveDocumentFileUrl(request.getAdditionalDocumentFileUrl());
        if (additionalFileUrl != null) {
            uploads.add(new DocumentUpload(
                    MemberDocumentType.OTHER, "Additional document", additionalFileUrl));
        }

        return uploads;
    }

    private int countRequestedDocuments(CompleteUserProfileRequest request) {
        return buildDocumentUploads(request).size();
    }

    private String resolveDocumentFileUrl(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            return null;
        }
        if (trimmed.startsWith("file://") || trimmed.length() > MAX_DOCUMENT_FILE_URL_LENGTH) {
            return PENDING_UPLOAD_FILE_URL;
        }
        return trimmed;
    }

    private int calculateCompletionPercentage(UserEntity user, int documentsUploaded) {
        int total = 9;
        int completed = 0;
        if (StringUtils.hasText(user.getFullName())
                && !"user".equals(user.getFullName().trim().toLowerCase(Locale.ROOT))) {
            completed++;
        }
        if (StringUtils.hasText(user.getProfilePhotoUrl())) {
            completed++;
        }
        if (user.getGender() != null) {
            completed++;
        }
        if (user.getDateOfBirth() != null) {
            completed++;
        }
        if (StringUtils.hasText(user.getEmail())) {
            completed++;
        }
        if (StringUtils.hasText(user.getPermanentAddress())) {
            completed++;
        }
        if (StringUtils.hasText(user.getCity())) {
            completed++;
        }
        if (StringUtils.hasText(user.getState())) {
            completed++;
        }
        if (StringUtils.hasText(user.getPincode())) {
            completed++;
        }
        if (documentsUploaded > 0) {
            completed++;
            total++;
        }
        return Math.min(100, Math.round((completed * 100f) / total));
    }

    private boolean hasEmergencyContact(CompleteUserProfileRequest request) {
        return StringUtils.hasText(request.getEmergencyContactName())
                || StringUtils.hasText(request.getEmergencyContactMobile())
                || StringUtils.hasText(request.getEmergencyContactRelation());
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private LocalDate parseDateOfBirth(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return LocalDate.parse(trimmed);
    }

    private UserEntity loadCurrentUserEntity() {
        UserPrincipal principal = getAuthenticatedPrincipal();
        return userRepository.findByIdAndIsActiveTrue(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId()));
    }

    private AuthTokenResponse toAuthTokenResponse(UserEntity user) {
        return AuthTokenResponse.builder()
                .accessToken(jwtService.generateToken(user))
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationMs())
                .user(UserResponse.from(user))
                .build();
    }

    /**
     * LOGIN and RESET_PASSWORD OTP is only dispatched when the mobile already has an account.
     * Unknown numbers still get a success response so callers cannot enumerate accounts.
     * REGISTER is the opposite: an existing account is a conflict, not a reason to send OTP.
     */
    private boolean shouldDispatchOtp(OtpPurpose purpose, boolean accountExists) {
        if (purpose == OtpPurpose.LOGIN || purpose == OtpPurpose.RESET_PASSWORD) {
            return accountExists;
        }
        return true;
    }

    private void rejectRegisterOtpForActiveMobile(String mobileNumber, OtpPurpose purpose) {
        if (purpose != OtpPurpose.REGISTER) {
            return;
        }
        if (userRepository.findByMobileNumberAndIsActiveTrue(mobileNumber).isPresent()) {
            throw new BusinessException("This mobile number is already registered.", HttpStatus.CONFLICT);
        }
    }

    private boolean passwordMatches(String rawPassword, UserEntity user) {
        boolean usable = user != null && StringUtils.hasText(user.getPasswordHash());
        String hash = usable ? user.getPasswordHash() : DUMMY_PASSWORD_HASH;
        try {
            return passwordEncoder.matches(rawPassword, hash) && usable;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private UserPrincipal getAuthenticatedPrincipal() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal;
        }
        throw new BusinessException("Invalid authentication context");
    }

    private record DocumentUpload(MemberDocumentType type, String number, String fileUrl) {}
}
