package com.amico.amico_backend.space.application.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.amico.amico_backend.member.domain.model.MembershipRole;
import com.amico.amico_backend.member.domain.model.MembershipStatus;
import com.amico.amico_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.amico.amico_backend.space.api.dto.response.SpacePermissionsResponse;
import com.amico.amico_backend.space.domain.model.SpaceType;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.amico.amico_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SpacePermissionPolicyTest {

    @ParameterizedTest(name = "{0} in PG: view={1} manage={2} deactivate={3} occupancy={4}")
    @CsvSource({
        "OWNER, true, true, true, true",
        "MANAGER, true, true, false, true",
        "STAFF, true, false, false, false",
        "TENANT, false, false, false, false",
        "CUSTOMER, false, false, false, false"
    })
    void forMembership_pgSpace(
            MembershipRole role,
            boolean canView,
            boolean canManage,
            boolean canDeactivate,
            boolean canManageOccupancy) {
        SpacePermissionsResponse permissions = SpacePermissionPolicy.forMembership(membership(role, SpaceType.PG));

        assertThat(permissions.isCanViewAccommodation()).isEqualTo(canView);
        assertThat(permissions.isCanManageAccommodation()).isEqualTo(canManage);
        assertThat(permissions.isCanDeactivateAccommodation()).isEqualTo(canDeactivate);
        assertThat(permissions.isCanManageOccupancy()).isEqualTo(canManageOccupancy);
        assertThat(permissions.isCanViewSpaceOccupancies()).isEqualTo(role != MembershipRole.TENANT && role != MembershipRole.CUSTOMER);
        assertThat(permissions.isCanManageMembers()).isEqualTo(canManageOccupancy);
        assertThat(permissions.isCanRemoveMember()).isEqualTo(role == MembershipRole.OWNER);
        assertThat(permissions.isCanManageMeals()).isEqualTo(canManageOccupancy);
        assertThat(permissions.isCanViewMeals()).isTrue();
        assertThat(permissions.isCanManageMealParticipation()).isEqualTo(canManageOccupancy);
        assertThat(permissions.isCanViewOwnMealParticipation()).isTrue();
        assertThat(permissions.isCanRaiseComplaint())
                .isEqualTo(role == MembershipRole.OWNER
                        || role == MembershipRole.MANAGER
                        || role == MembershipRole.TENANT
                        || role == MembershipRole.CUSTOMER);
        assertThat(permissions.isCanViewAllComplaints()).isEqualTo(canManageOccupancy);
        assertThat(permissions.isCanManageComplaints()).isEqualTo(canManageOccupancy);
    }

    @ParameterizedTest
    @CsvSource({"OWNER", "MANAGER", "STAFF", "TENANT", "CUSTOMER"})
    void forMembership_messSpace_deniesAccommodation(MembershipRole role) {
        SpacePermissionsResponse permissions = SpacePermissionPolicy.forMembership(membership(role, SpaceType.MESS));

        assertThat(permissions.isCanViewAccommodation()).isFalse();
        assertThat(permissions.isCanManageAccommodation()).isFalse();
        assertThat(permissions.isCanDeactivateAccommodation()).isFalse();
    }

    private SpaceMembershipEntity membership(MembershipRole role, SpaceType spaceType) {
        UserEntity user = UserEntity.builder().fullName("User").mobileNumber("9000000000").build();
        user.setId(UUID.randomUUID());
        SpaceEntity space = SpaceEntity.builder()
                .owner(user)
                .name("Test Space")
                .type(spaceType)
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
