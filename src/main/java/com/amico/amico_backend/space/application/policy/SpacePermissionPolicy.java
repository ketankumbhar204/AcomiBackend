package com.amico.amico_backend.space.application.policy;

import com.amico.amico_backend.member.domain.model.MembershipRole;
import com.amico.amico_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.amico.amico_backend.space.api.dto.response.SpacePermissionsResponse;
import com.amico.amico_backend.space.domain.model.SpaceType;

public final class SpacePermissionPolicy {

    private SpacePermissionPolicy() {}

    public static SpacePermissionsResponse forMembership(SpaceMembershipEntity membership) {
        MembershipRole role = membership.getRole();
        SpaceType spaceType = membership.getSpace().getType();
        boolean accommodationApplicable = spaceType != SpaceType.MESS;

        boolean owner = role == MembershipRole.OWNER;
        boolean manager = role == MembershipRole.MANAGER;
        boolean staff = role == MembershipRole.STAFF;
        boolean ownerOrManager = owner || manager;

        boolean tenantOrCustomer =
                role == MembershipRole.TENANT || role == MembershipRole.CUSTOMER;

        return SpacePermissionsResponse.builder()
                .canViewAccommodation(accommodationApplicable && (owner || manager || staff))
                .canManageAccommodation(accommodationApplicable && ownerOrManager)
                .canDeactivateAccommodation(accommodationApplicable && owner)
                .canManageOccupancy(ownerOrManager)
                .canViewSpaceOccupancies(owner || manager || staff)
                .canManageMembers(ownerOrManager)
                .canRemoveMember(owner)
                .canManageMeals(ownerOrManager)
                .canViewMeals(true)
                .canManageMealParticipation(ownerOrManager)
                .canViewOwnMealParticipation(true)
                .canRaiseComplaint(ownerOrManager || tenantOrCustomer)
                .canViewAllComplaints(ownerOrManager)
                .canManageComplaints(ownerOrManager)
                .build();
    }
}
