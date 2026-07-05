package com.countin.countin_backend.auth.application.service;

import com.countin.countin_backend.auth.api.dto.request.SendOtpRequest;
import com.countin.countin_backend.auth.api.dto.request.VerifyOtpRequest;
import com.countin.countin_backend.auth.api.dto.response.AuthTokenResponse;
import com.countin.countin_backend.auth.api.dto.response.SendOtpResponse;
import com.countin.countin_backend.common.exception.BusinessException;
import com.countin.countin_backend.common.exception.ResourceNotFoundException;
import com.countin.countin_backend.common.util.MobileNumberNormalizer;
import com.countin.countin_backend.config.security.JwtService;
import com.countin.countin_backend.config.security.UserPrincipal;
import com.countin.countin_backend.member.domain.model.MemberDocumentType;
import com.countin.countin_backend.member.infrastructure.persistence.entity.MemberDocumentEntity;
import com.countin.countin_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.countin.countin_backend.member.infrastructure.persistence.repository.MemberDocumentRepository;
import com.countin.countin_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.countin.countin_backend.user.api.dto.request.CompleteUserProfileRequest;
import com.countin.countin_backend.user.api.dto.request.UpdateUserRequest;
import com.countin.countin_backend.user.api.dto.response.UserResponse;
import com.countin.countin_backend.user.domain.model.KycStatus;
import com.countin.countin_backend.user.domain.model.ProfileStatus;
import com.countin.countin_backend.user.infrastructure.persistence.entity.UserEntity;
import com.countin.countin_backend.user.infrastructure.persistence.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
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

    public SendOtpResponse sendOtp(SendOtpRequest request) {
        String mobileNumber = MobileNumberNormalizer.normalize(request.getMobileNumber());
        otpService.sendOtp(mobileNumber);

        return SendOtpResponse.builder()
                .mobileNumber(mobileNumber)
                .message("OTP sent successfully")
                .build();
    }

    @Transactional
    public AuthTokenResponse verifyOtp(VerifyOtpRequest request) {
        String mobileNumber = MobileNumberNormalizer.normalize(request.getMobileNumber());
        otpService.verifyOtp(mobileNumber, request.getOtp());

        UserEntity user = userRepository.findByMobileNumber(mobileNumber)
                .orElseGet(() -> createUser(mobileNumber));

        if (!user.isActive()) {
            throw new BusinessException("User account is inactive");
        }

        String token = jwtService.generateToken(user);

        return AuthTokenResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationMs())
                .user(UserResponse.from(user))
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

    private UserEntity createUser(String mobileNumber) {
        return userRepository.save(UserEntity.builder()
                .mobileNumber(mobileNumber)
                .fullName("User")
                .build());
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
