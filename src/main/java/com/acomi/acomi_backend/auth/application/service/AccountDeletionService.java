package com.acomi.acomi_backend.auth.application.service;

import com.acomi.acomi_backend.auth.api.dto.request.PasswordAccountDeletionRequest;
import com.acomi.acomi_backend.auth.api.dto.request.VerifyOtpRequest;
import com.acomi.acomi_backend.auth.domain.model.OtpPurpose;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.util.MobileNumberNormalizer;
import com.acomi.acomi_backend.member.domain.model.InvitationStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.InvitationEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberDocumentEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.InvitationRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberDocumentRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.notification.infrastructure.persistence.repository.SpaceNotificationRepository;
import com.acomi.acomi_backend.user.domain.model.KycStatus;
import com.acomi.acomi_backend.user.domain.model.ProfileStatus;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Single account/data deletion implementation used by in-app JWT deletion
 * and the public web password/OTP deletion resources.
 */
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    static final String DELETED_USER_DISPLAY_NAME = "Deleted user";
    static final String DELETED_MEMBER_DISPLAY_NAME = "Deleted member";
    static final String DELETED_MOBILE_PREFIX = "deleted_";
    private static final String INVALID_CREDENTIALS = "Invalid mobile number or password.";
    private static final String DUMMY_PASSWORD_HASH =
            "{bcrypt}$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG";

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final MemberRepository memberRepository;
    private final MemberDocumentRepository memberDocumentRepository;
    private final SpaceMembershipRepository spaceMembershipRepository;
    private final InvitationRepository invitationRepository;
    private final SpaceNotificationRepository spaceNotificationRepository;

    @Transactional
    public void deleteAuthenticatedAccount() {
        UUID userId = SecurityUtils.getCurrentUserId();
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        deleteAccount(user);
    }

    /**
     * Public web deletion: OTP proves possession of the mobile number.
     * Does not create a user if none exists. Already-deleted / unknown numbers
     * return successfully so callers cannot enumerate accounts.
     */
    @Transactional
    public void deleteAccountByOtp(VerifyOtpRequest request) {
        String mobileNumber = MobileNumberNormalizer.normalize(request.getMobileNumber());
        otpService.verifyAndConsume(mobileNumber, request.getOtp(), OtpPurpose.ACCOUNT_DELETION);

        userRepository.findByMobileNumberAndIsActiveTrue(mobileNumber)
                .ifPresent(this::deleteAccount);
    }

    /**
     * Public web deletion using password authentication.
     * Unknown numbers, deleted accounts, and wrong passwords all return the same error
     * so callers cannot enumerate accounts.
     */
    @Transactional
    public void deleteAccountByPassword(PasswordAccountDeletionRequest request) {
        String mobileNumber = MobileNumberNormalizer.normalize(request.getMobileNumber());
        UserEntity user = userRepository.findByMobileNumberAndIsActiveTrue(mobileNumber).orElse(null);
        if (!passwordMatches(request.getPassword(), user)) {
            throw new BusinessException(INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);
        }
        deleteAccount(user);
    }

    private void deleteAccount(UserEntity user) {
        LocalDateTime now = LocalDateTime.now();

        spaceMembershipRepository.deactivateAllActiveMembershipsForUser(user.getId(), now);
        cancelPendingInvitationsSentBy(user);
        spaceNotificationRepository.deleteByUserId(user.getId());
        anonymizeLinkedMembers(user);
        anonymizeUser(user, now);

        userRepository.save(user);
        log.info("Account deleted userId={}", user.getId());
    }

    private void cancelPendingInvitationsSentBy(UserEntity user) {
        List<InvitationEntity> pending = invitationRepository.findByInvitedBy_IdAndStatus(
                user.getId(), InvitationStatus.PENDING);
        for (InvitationEntity invitation : pending) {
            invitation.setStatus(InvitationStatus.CANCELLED);
            invitationRepository.save(invitation);
        }
    }

    private void anonymizeLinkedMembers(UserEntity user) {
        for (MemberEntity member : memberRepository.findByUser_Id(user.getId())) {
            List<MemberDocumentEntity> documents =
                    memberDocumentRepository.findByMemberIdOrderByUploadedAtDesc(member.getId());
            if (!documents.isEmpty()) {
                memberDocumentRepository.deleteAll(documents);
            }

            member.setFullName(DELETED_MEMBER_DISPLAY_NAME);
            member.setMobileNumber(anonymizedMemberMobile(member.getId()));
            member.setEmergencyContactName(null);
            member.setEmergencyContactRelation(null);
            member.setEmergencyContactMobile(null);
            member.setUser(null);
            memberRepository.save(member);
        }
    }

    private void anonymizeUser(UserEntity user, LocalDateTime now) {
        user.setMobileNumber(DELETED_MOBILE_PREFIX + user.getId());
        user.setFullName(DELETED_USER_DISPLAY_NAME);
        user.setProfilePhotoUrl(null);
        user.setEmail(null);
        user.setGender(null);
        user.setDateOfBirth(null);
        user.setPermanentAddress(null);
        user.setCity(null);
        user.setState(null);
        user.setPincode(null);
        user.setProfileCompleted(false);
        user.setProfileCompletedAt(null);
        user.setProfileStatus(ProfileStatus.PENDING);
        user.setProfileCompletionPercentage(0);
        user.setDocumentsUploaded(0);
        user.setKycStatus(KycStatus.NOT_STARTED);
        user.setPasswordHash(null);
        user.setMobileVerifiedAt(null);
        user.setActive(false);
        if (user.getDeletedAt() == null) {
            user.setDeletedAt(now);
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

    static String anonymizedMemberMobile(UUID memberId) {
        String hex = memberId.toString().replace("-", "");
        return ("d" + hex).substring(0, 15);
    }
}
