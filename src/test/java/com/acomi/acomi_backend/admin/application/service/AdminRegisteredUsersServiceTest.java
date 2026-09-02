package com.acomi.acomi_backend.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.admin.api.dto.response.AdminRegisteredUserResponse;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.domain.model.MembershipStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.domain.model.SystemRole;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AdminRegisteredUsersServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    private AdminRegisteredUsersService service;

    @BeforeEach
    void setUp() {
        service = new AdminRegisteredUsersService(userRepository, spaceMembershipRepository);
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
    void list_doesNotQueryMembershipsWhenNoVerifiedUsers() {
        when(userRepository.findByMobileVerifiedAtIsNotNullAndIsActiveTrueAndSystemRole(
                        eq(SystemRole.USER), any(Pageable.class)))
                .thenReturn(Page.empty());

        Page<AdminRegisteredUserResponse> page = service.list(PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        verify(spaceMembershipRepository, never()).findActiveByUserIdsWithSpace(any());
    }

    @Test
    void unverifiedUsersAreExcludedByRepositoryContract() {
        when(userRepository.countByMobileVerifiedAtIsNotNullAndIsActiveTrueAndSystemRole(SystemRole.USER))
                .thenReturn(0L);

        assertThat(service.countRegisteredUsers()).isZero();
        verify(userRepository)
                .countByMobileVerifiedAtIsNotNullAndIsActiveTrueAndSystemRole(SystemRole.USER);
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
