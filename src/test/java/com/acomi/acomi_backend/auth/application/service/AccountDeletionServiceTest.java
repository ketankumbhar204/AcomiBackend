package com.acomi.acomi_backend.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.auth.api.dto.request.OtpVerifiedActionRequest;
import com.acomi.acomi_backend.auth.api.dto.request.PasswordAccountDeletionRequest;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.config.security.UserPrincipal;
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
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    @Mock
    private OtpService otpService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberDocumentRepository memberDocumentRepository;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private SpaceNotificationRepository spaceNotificationRepository;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @InjectMocks
    private AccountDeletionService accountDeletionService;

    private UUID userId;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = UserEntity.builder()
                .mobileNumber("9876543210")
                .fullName("Priya Sharma")
                .email("priya@example.com")
                .dateOfBirth(LocalDate.of(1994, 1, 15))
                .permanentAddress("12 MG Road")
                .city("Pune")
                .state("Maharashtra")
                .pincode("411001")
                .profilePhotoUrl("https://example.com/photo.jpg")
                .passwordHash("{bcrypt}$2a$10$existingHashValueThatWillBeCleared0123456789ab")
                .isActive(true)
                .build();
        user.setId(userId);
        authenticateAs(user);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deleteAuthenticatedAccount_removesPersonalDataAndMemberships() {
        UUID memberId = UUID.randomUUID();
        MemberEntity member = MemberEntity.builder()
                .fullName("Priya Sharma")
                .mobileNumber("9876543210")
                .emergencyContactName("Amit")
                .emergencyContactMobile("9123456789")
                .user(user)
                .build();
        member.setId(memberId);
        MemberDocumentEntity document = MemberDocumentEntity.builder()
                .member(member)
                .documentNumber("ABCDE1234F")
                .fileUrl("https://example.com/id.jpg")
                .build();
        InvitationEntity invitation = InvitationEntity.builder()
                .invitedBy(user)
                .mobileNumber("9988776655")
                .status(InvitationStatus.PENDING)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(memberRepository.findByUser_Id(userId)).thenReturn(List.of(member));
        when(memberDocumentRepository.findByMemberIdOrderByUploadedAtDesc(memberId))
                .thenReturn(List.of(document));
        when(invitationRepository.findByInvitedBy_IdAndStatus(userId, InvitationStatus.PENDING))
                .thenReturn(List.of(invitation));

        accountDeletionService.deleteAuthenticatedAccount();

        assertThat(user.isActive()).isFalse();
        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(user.getMobileNumber()).isEqualTo("deleted_" + userId);
        assertThat(user.getFullName()).isEqualTo("Deleted user");
        assertThat(user.getEmail()).isNull();
        assertThat(user.getProfilePhotoUrl()).isNull();
        assertThat(user.getPermanentAddress()).isNull();
        assertThat(user.getKycStatus()).isEqualTo(KycStatus.NOT_STARTED);
        assertThat(user.getPasswordHash()).isNull();
        assertThat(member.getFullName()).isEqualTo("Deleted member");
        assertThat(member.getMobileNumber()).isEqualTo(AccountDeletionService.anonymizedMemberMobile(memberId));
        assertThat(member.getEmergencyContactName()).isNull();
        assertThat(member.getUser()).isNull();
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.CANCELLED);
        verify(spaceMembershipRepository)
                .deactivateAllActiveMembershipsForUser(eq(userId), any(LocalDateTime.class));
        verify(spaceNotificationRepository).deleteByUserId(userId);
        verify(memberDocumentRepository).deleteAll(List.of(document));
        verify(memberRepository).save(member);
        verify(userRepository).save(user);
    }

    @Test
    void deleteAuthenticatedAccount_usesAuthenticatedPrincipalOnly() {
        UUID otherUserId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(memberRepository.findByUser_Id(userId)).thenReturn(List.of());
        when(invitationRepository.findByInvitedBy_IdAndStatus(userId, InvitationStatus.PENDING))
                .thenReturn(List.of());

        accountDeletionService.deleteAuthenticatedAccount();

        verify(userRepository).findById(userId);
        verify(userRepository, never()).findById(otherUserId);
        verify(spaceMembershipRepository, never())
                .deactivateAllActiveMembershipsForUser(eq(otherUserId), any());
    }

    @Test
    void deleteAuthenticatedAccount_unauthenticated_isRejected() {
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("anonymous", null));

        assertThatThrownBy(() -> accountDeletionService.deleteAuthenticatedAccount())
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid authentication context");

        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteAuthenticatedAccount_whenMembershipUpdateFails_doesNotSaveUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(spaceMembershipRepository.deactivateAllActiveMembershipsForUser(
                        eq(userId), any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("membership update failed"));

        assertThatThrownBy(() -> accountDeletionService.deleteAuthenticatedAccount())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("membership update failed");

        assertThat(user.isActive()).isTrue();
        assertThat(user.getMobileNumber()).isEqualTo("9876543210");
        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteAuthenticatedAccount_duplicateDeletion_isIdempotent() {
        user.setActive(false);
        user.setDeletedAt(LocalDateTime.now().minusDays(1));
        user.setMobileNumber("deleted_" + userId);
        user.setFullName("Deleted user");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(memberRepository.findByUser_Id(userId)).thenReturn(List.of());
        when(invitationRepository.findByInvitedBy_IdAndStatus(userId, InvitationStatus.PENDING))
                .thenReturn(List.of());

        accountDeletionService.deleteAuthenticatedAccount();

        assertThat(user.getMobileNumber()).isEqualTo("deleted_" + userId);
        verify(userRepository).save(user);
    }

    @Test
    void deleteAccountByOtp_invalidToken_doesNotDelete() {
        OtpVerifiedActionRequest request = otpTokenRequest("9876543210", "bad-token");
        org.mockito.Mockito.doThrow(new BusinessException("Invalid or expired verification token."))
                .when(otpService)
                .consumeVerificationToken(
                        "9876543210",
                        "bad-token",
                        com.acomi.acomi_backend.auth.domain.model.OtpPurpose.ACCOUNT_DELETION);

        assertThatThrownBy(() -> accountDeletionService.deleteAccountByOtp(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid or expired verification token.");

        verify(userRepository, never()).save(any());
        verify(spaceMembershipRepository, never())
                .deactivateAllActiveMembershipsForUser(any(), any());
    }

    @Test
    void deleteAccountByOtp_unknownOrAlreadyDeletedMobile_isIdempotent() {
        OtpVerifiedActionRequest request = otpTokenRequest("9876543210", "delete-token");
        when(userRepository.findByMobileNumberAndIsActiveTrue("9876543210"))
                .thenReturn(Optional.empty());

        accountDeletionService.deleteAccountByOtp(request);

        verify(otpService).consumeVerificationToken(
                "9876543210",
                "delete-token",
                com.acomi.acomi_backend.auth.domain.model.OtpPurpose.ACCOUNT_DELETION);
        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteAccountByOtp_deletesMatchingActiveAccount() {
        OtpVerifiedActionRequest request = otpTokenRequest("9876543210", "delete-token");
        when(userRepository.findByMobileNumberAndIsActiveTrue("9876543210"))
                .thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(memberRepository.findByUser_Id(userId)).thenReturn(List.of());
        when(invitationRepository.findByInvitedBy_IdAndStatus(userId, InvitationStatus.PENDING))
                .thenReturn(List.of());

        accountDeletionService.deleteAccountByOtp(request);

        assertThat(user.isActive()).isFalse();
        assertThat(user.getMobileNumber()).startsWith("deleted_");
        verify(userRepository).save(user);
    }

    @Test
    void deleteAccountByPassword_validCredentials_deletesAccount() {
        user.setPasswordHash("{bcrypt}hashed");
        PasswordAccountDeletionRequest request = passwordRequest("9876543210", "Secret12");
        when(passwordEncoder.matches("Secret12", "{bcrypt}hashed")).thenReturn(true);
        when(userRepository.findByMobileNumberAndIsActiveTrue("9876543210"))
                .thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(memberRepository.findByUser_Id(userId)).thenReturn(List.of());
        when(invitationRepository.findByInvitedBy_IdAndStatus(userId, InvitationStatus.PENDING))
                .thenReturn(List.of());

        accountDeletionService.deleteAccountByPassword(request);

        assertThat(user.isActive()).isFalse();
        assertThat(user.getPasswordHash()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void deleteAccountByPassword_wrongPassword_doesNotDelete() {
        user.setPasswordHash("{bcrypt}hashed");
        PasswordAccountDeletionRequest request = passwordRequest("9876543210", "WrongPass");
        when(userRepository.findByMobileNumberAndIsActiveTrue("9876543210"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> accountDeletionService.deleteAccountByPassword(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid mobile number or password.");

        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteAccountByPassword_unknownMobile_doesNotRevealExistence() {
        PasswordAccountDeletionRequest request = passwordRequest("9876543210", "Secret12");
        when(userRepository.findByMobileNumberAndIsActiveTrue("9876543210"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountDeletionService.deleteAccountByPassword(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid mobile number or password.");

        verify(userRepository, never()).save(any());
    }

    private void authenticateAs(UserEntity entity) {
        UserPrincipal principal = new UserPrincipal(entity);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private static OtpVerifiedActionRequest otpTokenRequest(String mobile, String token) {
        OtpVerifiedActionRequest request = new OtpVerifiedActionRequest();
        ReflectionTestUtils.setField(request, "mobileNumber", mobile);
        ReflectionTestUtils.setField(request, "verificationToken", token);
        return request;
    }

    private static PasswordAccountDeletionRequest passwordRequest(String mobile, String password) {
        PasswordAccountDeletionRequest request = new PasswordAccountDeletionRequest();
        ReflectionTestUtils.setField(request, "mobileNumber", mobile);
        ReflectionTestUtils.setField(request, "password", password);
        return request;
    }
}
