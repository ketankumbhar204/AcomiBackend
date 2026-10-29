package com.acomi.acomi_backend.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acomi.acomi_backend.admin.api.dto.request.AdminResetPasswordRequest;
import com.acomi.acomi_backend.admin.api.dto.response.AdminRegisteredUserResponse;
import com.acomi.acomi_backend.admin.application.AdminSecurityAudit;
import com.acomi.acomi_backend.admin.application.LoggingAdminSecurityAudit;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand;
import com.acomi.acomi_backend.notification.application.service.NotificationService;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.auth.application.service.AccountDeletionService;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.domain.model.MembershipStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.common.time.AcomiTime;
import com.acomi.acomi_backend.user.domain.model.SystemRole;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminRegisteredUsersServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    @Mock
    private AccountDeletionService accountDeletionService;

    @Mock
    private AdminSecurityAudit adminSecurityAudit;

    @Mock
    private NotificationService notificationService;

    private final PasswordEncoder passwordEncoder =
            PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private AdminRegisteredUsersService service;

    @BeforeEach
    void setUp() {
        service = new AdminRegisteredUsersService(
                userRepository,
                spaceMembershipRepository,
                accountDeletionService,
                passwordEncoder,
                org.mockito.Mockito.mock(
                        com.acomi.acomi_backend.space.application.service.SpaceService.class),
                org.mockito.Mockito.mock(
                        com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository
                                .class),
                org.mockito.Mockito.mock(
                        com.acomi.acomi_backend.member.application.service.InvitationProvisioner.class),
                org.mockito.Mockito.mock(
                        com.acomi.acomi_backend.member.application.service.InvitationService.class),
                adminSecurityAudit,
                notificationService);
    }

    @Test
    void countRegisteredUsers_usesPhoneVerifiedActiveAppUsers() {
        when(userRepository.countByMobileVerifiedAtIsNotNullAndIsActiveTrueAndSystemRole(SystemRole.USER))
                .thenReturn(12L);

        assertThat(service.countRegisteredUsers()).isEqualTo(12L);
        verify(userRepository)
                .countByMobileVerifiedAtIsNotNullAndIsActiveTrueAndSystemRole(SystemRole.USER);
    }

    @Test
    void list_includesVerifiedOwner() {
        UserEntity user = verifiedUser("Owner One", "9000000002");
        SpaceMembershipEntity membership = membership(user, "Sunrise PG", SpaceType.PG, MembershipRole.OWNER);
        stubList(List.of(user), List.of(membership));

        AdminRegisteredUserResponse item = service.list(PageRequest.of(0, 20)).getContent().get(0);

        assertThat(item.getSelectedRole()).isEqualTo(AdminRegisteredUsersService.ROLE_OWNER);
        assertThat(item.getOnboardingStatus()).isEqualTo(AdminRegisteredUsersService.ONBOARDING_COMPLETE);
        assertThat(item.isMobileVerified()).isTrue();
    }

    @Test
    void list_includesVerifiedMember() {
        UserEntity user = verifiedUser("Member One", "9000000003");
        SpaceMembershipEntity membership =
                membership(user, "City Mess", SpaceType.MESS, MembershipRole.CUSTOMER);
        stubList(List.of(user), List.of(membership));

        AdminRegisteredUserResponse item = service.list(PageRequest.of(0, 20)).getContent().get(0);

        assertThat(item.getSelectedRole()).isEqualTo(AdminRegisteredUsersService.ROLE_MEMBER);
        assertThat(item.getOnboardingStatus()).isEqualTo(AdminRegisteredUsersService.ONBOARDING_COMPLETE);
    }

    @Test
    void list_includesVerifiedUserWithNoRoleSelected() {
        UserEntity user = verifiedUser("Abandoned Onboarding", "9000000004");
        stubList(List.of(user), List.of());

        AdminRegisteredUserResponse item = service.list(PageRequest.of(0, 20)).getContent().get(0);

        assertThat(item.getSelectedRole()).isEqualTo(AdminRegisteredUsersService.ROLE_NOT_SELECTED);
        assertThat(item.getOnboardingStatus()).isEqualTo(AdminRegisteredUsersService.ONBOARDING_INCOMPLETE);
        assertThat(item.getSpaces()).isEmpty();
        assertThat(item.isProfileCompleted()).isFalse();
        assertThat(item.isMobileVerified()).isTrue();
    }

    @Test
    void list_includesIncompleteOnboardingWithoutRequiringProfile() {
        UserEntity user = verifiedUser("Incomplete", "9000000005");
        user.setProfileCompleted(false);
        stubList(List.of(user), List.of());

        AdminRegisteredUserResponse item = service.list(PageRequest.of(0, 20)).getContent().get(0);

        assertThat(item.getOnboardingStatus()).isEqualTo(AdminRegisteredUsersService.ONBOARDING_INCOMPLETE);
        assertThat(item.isProfileCompleted()).isFalse();
    }

    @Test
    void list_countsUserWithPropertyOnce() {
        UserEntity user = verifiedUser("Property Owner", "9000000006");
        stubList(
                List.of(user),
                List.of(membership(user, "PG One", SpaceType.PG, MembershipRole.OWNER)));

        Page<AdminRegisteredUserResponse> page = service.list(PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void list_countsUserWithMessOnce() {
        UserEntity user = verifiedUser("Mess Owner", "9000000007");
        stubList(
                List.of(user),
                List.of(membership(user, "Mess One", SpaceType.MESS, MembershipRole.OWNER)));

        assertThat(service.list(PageRequest.of(0, 20)).getContent()).hasSize(1);
    }

    @Test
    void list_countsUserWithMultipleSpacesOnce() {
        UserEntity user = verifiedUser("Multi Owner", "9000000008");
        stubList(
                List.of(user),
                List.of(
                        membership(user, "PG A", SpaceType.PG, MembershipRole.OWNER),
                        membership(user, "Hostel B", SpaceType.HOSTEL, MembershipRole.OWNER),
                        membership(user, "Mess C", SpaceType.MESS, MembershipRole.OWNER)));

        List<AdminRegisteredUserResponse> content = service.list(PageRequest.of(0, 20)).getContent();

        assertThat(content).hasSize(1);
        assertThat(content.get(0).getSpaces()).hasSize(3);
        assertThat(content.get(0).getSelectedRole()).isEqualTo(AdminRegisteredUsersService.ROLE_OWNER);
    }

    @Test
    void search_includesEmailOnRegisteredUser() {
        UserEntity user = verifiedUser("Email Owner", "9000000009");
        user.setEmail("rahul@example.com");
        when(userRepository.searchVerifiedUsersFiltered(
                        eq(SystemRole.USER),
                        eq("rahul@example.com"),
                        eq(false),
                        any(LocalDateTime.class),
                        eq(false),
                        any(LocalDateTime.class),
                        eq(-1),
                        eq(""),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user), PageRequest.of(0, 10), 1));
        when(spaceMembershipRepository.findActiveByUserIdsWithSpace(List.of(user.getId()))).thenReturn(List.of());

        AdminRegisteredUserResponse item =
                service.search("rahul@example.com", PageRequest.of(0, 10)).getContent().get(0);

        assertThat(item.getEmail()).isEqualTo("rahul@example.com");
        assertThat(item.getFullName()).isEqualTo("Email Owner");
    }

    @Test
    void list_doesNotQueryMembershipsWhenNoVerifiedUsers() {
        when(userRepository.findByMobileVerifiedAtIsNotNullAndIsActiveTrueAndSystemRole(
                        eq(SystemRole.USER), any(Pageable.class)))
                .thenReturn(Page.empty());

        Page<AdminRegisteredUserResponse> page = service.list(PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        verify(spaceMembershipRepository, never()).findActiveByUserIdsWithSpace(any());
    }

    @Test
    void list_keepsAccountStatusSeparateFromVerificationAndOnboarding() {
        UserEntity user = verifiedUser("Verified Inactive", "9000000011");
        user.setActive(false);
        user.setProfileCompleted(true);
        user.setActivityTracked(false);
        stubList(List.of(user), List.of(membership(user, "Sunrise PG", SpaceType.PG, MembershipRole.OWNER)));

        AdminRegisteredUserResponse item = service.list(PageRequest.of(0, 20)).getContent().get(0);

        assertThat(item.isActive()).isFalse();
        assertThat(item.isMobileVerified()).isTrue();
        assertThat(item.isProfileCompleted()).isTrue();
        assertThat(item.getOnboardingStatus()).isEqualTo(AdminRegisteredUsersService.ONBOARDING_COMPLETE);
        assertThat(item.isActivityTracked()).isFalse();
        assertThat(item.getLastLoginAt()).isNull();
        assertThat(item.getLastActiveAt()).isNull();
    }

    @Test
    void getById_reportsRecordedActivityWithKolkataOffsetAndNeverConfusesItWithUpdatedAt() {
        UserEntity user = verifiedUser("Active Member", "9000000012");
        LocalDateTime recorded = LocalDateTime.of(2026, 10, 17, 17, 16);
        user.setActive(true);
        user.setActivityTracked(true);
        user.setLastLoginAt(recorded.minusHours(2));
        user.setLastActiveAt(recorded);
        user.setUpdatedAt(recorded.minusDays(3));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(spaceMembershipRepository.findActiveByUserIdsWithSpace(List.of(user.getId()))).thenReturn(List.of());

        AdminRegisteredUserResponse item = service.getById(user.getId());

        assertThat(item.isActive()).isTrue();
        assertThat(item.isActivityTracked()).isTrue();
        assertThat(item.getLastLoginAt()).isEqualTo(AcomiTime.toOffset(recorded.minusHours(2)));
        assertThat(item.getLastActiveAt()).isEqualTo(AcomiTime.toOffset(recorded));
        assertThat(item.getLastActiveAt().getOffset().getTotalSeconds()).isEqualTo(5 * 60 * 60 + 30 * 60);
        assertThat(item.getUpdatedAt()).isEqualTo(recorded.minusDays(3));
    }

    @Test
    void getById_trackedAccountWithNoTimestampsHasNotSignedIn() {
        UserEntity user = verifiedUser("Created Only", "9000000013");
        user.setActivityTracked(true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(spaceMembershipRepository.findActiveByUserIdsWithSpace(List.of(user.getId()))).thenReturn(List.of());

        AdminRegisteredUserResponse item = service.getById(user.getId());

        assertThat(item.isActive()).isTrue();
        assertThat(item.isActivityTracked()).isTrue();
        assertThat(item.getLastLoginAt()).isNull();
        assertThat(item.getLastActiveAt()).isNull();
    }

    @Test
    void unverifiedUsersAreExcludedByRepositoryContract() {
        when(userRepository.countByMobileVerifiedAtIsNotNullAndIsActiveTrueAndSystemRole(SystemRole.USER))
                .thenReturn(0L);

        assertThat(service.countRegisteredUsers()).isZero();
        verify(userRepository)
                .countByMobileVerifiedAtIsNotNullAndIsActiveTrueAndSystemRole(SystemRole.USER);
    }

    @Test
    void update_changesProfileFieldsAndOptionalPassword() {
        UserEntity user = verifiedUser("Old Name", "9876500099");
        user.setEmail("old@example.com");
        user.setPasswordHash(passwordEncoder.encode("OldSecret1"));
        when(userRepository.findById(user.getId())).thenReturn(java.util.Optional.of(user));
        when(userRepository.findByMobileNumberAndIsActiveTrue("9876500088")).thenReturn(java.util.Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(spaceMembershipRepository.findActiveByUserIdsWithSpace(List.of(user.getId())))
                .thenReturn(List.of());

        com.acomi.acomi_backend.admin.api.dto.request.AdminUpdateRegisteredUserRequest request =
                new com.acomi.acomi_backend.admin.api.dto.request.AdminUpdateRegisteredUserRequest();
        request.setFullName("New Name");
        request.setMobileNumber("9876500088");
        request.setEmail("new@example.com");
        request.setPassword("NewSecret1");
        request.setConfirmPassword("NewSecret1");

        AdminRegisteredUserResponse response = service.update(user.getId(), request);

        assertThat(user.getFullName()).isEqualTo("New Name");
        assertThat(user.getMobileNumber()).isEqualTo("9876500088");
        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getSystemRole()).isEqualTo(SystemRole.USER);
        assertThat(passwordEncoder.matches("NewSecret1", user.getPasswordHash())).isTrue();
        assertThat(response.getFullName()).isEqualTo("New Name");
        assertThat(response.getMobileNumber()).isEqualTo("9876500088");
        verify(userRepository).save(user);
    }

    private void stubList(List<UserEntity> users, List<SpaceMembershipEntity> memberships) {
        when(userRepository.findByMobileVerifiedAtIsNotNullAndIsActiveTrueAndSystemRole(
                        eq(SystemRole.USER), any(Pageable.class)))
                .thenReturn(new PageImpl<>(users, PageRequest.of(0, 20), users.size()));
        if (!users.isEmpty()) {
            when(spaceMembershipRepository.findActiveByUserIdsWithSpace(users.stream().map(UserEntity::getId).toList()))
                    .thenReturn(memberships);
        }
    }

    @Test
    void resetPassword_setsNewHashInvalidatesSessionsAndKeepsRole() {
        UserEntity user = verifiedUser("Member", "9876543210");
        String previous = passwordEncoder.encode("OldSecret1");
        user.setPasswordHash(previous);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminResetPasswordRequest request = new AdminResetPasswordRequest();
        request.setPassword("NewSecret1");
        request.setConfirmPassword("NewSecret1");
        UUID actorId = UUID.randomUUID();

        service.resetPassword(actorId, user.getId(), request);

        assertThat(passwordEncoder.matches("NewSecret1", user.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("OldSecret1", user.getPasswordHash())).isFalse();
        assertThat(user.getPasswordHash()).doesNotContain("NewSecret1").doesNotContain("OldSecret1");
        assertThat(user.getCredentialsChangedAt()).isNotNull();
        assertThat(user.getSystemRole()).isEqualTo(SystemRole.USER);
        assertThat(user.isActive()).isTrue();
        verify(spaceMembershipRepository, never()).save(any());
        verify(adminSecurityAudit)
                .record(actorId, user.getId(), LoggingAdminSecurityAudit.PASSWORD_RESET, "SUCCESS");
        ArgumentCaptor<PublishNotificationCommand> notification =
                ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService).publish(notification.capture());
        PublishNotificationCommand command = notification.getValue();
        assertThat(command.getNotificationType()).isEqualTo(NotificationType.PASSWORD_CHANGED);
        assertThat(command.getUserId()).isEqualTo(user.getId());
        assertThat(command.getSpaceId()).isNull();
        assertThat(command.getTitle()).doesNotContain("NewSecret1").doesNotContain("OldSecret1");
        assertThat(command.getMessage()).doesNotContain("NewSecret1").doesNotContain("OldSecret1");
        assertThat(command.getDedupeKey()).doesNotContain("NewSecret1").doesNotContain("OldSecret1");
    }

    @Test
    void resetPassword_rejectsSelfAndMismatchWithoutWritingPassword() {
        UserEntity user = verifiedUser("Admin", "9876543211");
        user.setPasswordHash(passwordEncoder.encode("OldSecret1"));

        AdminResetPasswordRequest self = new AdminResetPasswordRequest();
        self.setPassword("NewSecret1");
        self.setConfirmPassword("NewSecret1");
        assertThatThrownBy(() -> service.resetPassword(user.getId(), user.getId(), self))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Use account recovery to change your own password.")
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(adminSecurityAudit)
                .record(user.getId(), user.getId(), LoggingAdminSecurityAudit.PASSWORD_RESET, "REJECTED_SELF");
        verify(userRepository, never()).save(any());

        AdminResetPasswordRequest mismatch = new AdminResetPasswordRequest();
        mismatch.setPassword("NewSecret1");
        mismatch.setConfirmPassword("OtherSecret");
        assertThatThrownBy(() -> service.resetPassword(UUID.randomUUID(), user.getId(), mismatch))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Passwords do not match");
        assertThat(passwordEncoder.matches("OldSecret1", user.getPasswordHash())).isTrue();
        verify(notificationService, never()).publish(any());
    }

    private static UserEntity verifiedUser(String name, String mobile) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 1, 10, 0);
        UserEntity user = UserEntity.builder()
                .fullName(name)
                .mobileNumber(mobile)
                .mobileVerifiedAt(now)
                .systemRole(SystemRole.USER)
                .isActive(true)
                .profileCompleted(false)
                .build();
        user.setId(UUID.randomUUID());
        user.setCreatedAt(now);
        return user;
    }

    private static SpaceMembershipEntity membership(
            UserEntity user, String spaceName, SpaceType type, MembershipRole role) {
        SpaceEntity space = SpaceEntity.builder()
                .owner(user)
                .name(spaceName)
                .type(type)
                .isActive(true)
                .build();
        space.setId(UUID.randomUUID());
        return SpaceMembershipEntity.builder()
                .user(user)
                .space(space)
                .role(role)
                .status(MembershipStatus.ACTIVE)
                .build();
    }
}
