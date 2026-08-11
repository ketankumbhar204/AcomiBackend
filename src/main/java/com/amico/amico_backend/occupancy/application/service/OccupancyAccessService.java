package com.amico.amico_backend.occupancy.application.service;

import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.member.application.service.SpaceMembershipResolver;
import com.amico.amico_backend.member.domain.model.MembershipRole;
import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.amico.amico_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OccupancyAccessService {

    private static final List<MembershipRole> MANAGE_OCCUPANCY_ROLES =
            List.of(MembershipRole.OWNER, MembershipRole.MANAGER);

    private static final List<MembershipRole> VIEW_SPACE_OCCUPANCIES_ROLES =
            List.of(MembershipRole.OWNER, MembershipRole.MANAGER, MembershipRole.STAFF);

    private static final List<MembershipRole> VIEW_MEMBER_OCCUPANCY_ROLES =
            List.of(MembershipRole.OWNER, MembershipRole.MANAGER, MembershipRole.STAFF);

    private final SpaceMembershipResolver membershipResolver;

    public void assertCanManageOccupancy(UUID spaceId, UUID callerId) {
        SpaceMembershipEntity membership = membershipResolver.requireActive(spaceId, callerId);
        if (!MANAGE_OCCUPANCY_ROLES.contains(membership.getRole())) {
            throw new BusinessException(
                    "Only OWNER or MANAGER can perform this action", HttpStatus.FORBIDDEN);
        }
    }

    public void assertCanViewSpaceOccupancies(UUID spaceId, UUID callerId) {
        SpaceMembershipEntity membership = membershipResolver.requireActive(spaceId, callerId);
        if (membership.getRole() == MembershipRole.CUSTOMER) {
            throw new BusinessException("Customers cannot access occupancy data", HttpStatus.FORBIDDEN);
        }
        if (membership.getRole() == MembershipRole.TENANT) {
            throw new BusinessException("Use member-specific occupancy endpoints", HttpStatus.FORBIDDEN);
        }
        if (!VIEW_SPACE_OCCUPANCIES_ROLES.contains(membership.getRole())) {
            throw new BusinessException("You do not have permission to view occupancies", HttpStatus.FORBIDDEN);
        }
    }

    public void assertCanViewMemberOccupancy(UUID spaceId, UUID memberId, UUID callerId, MemberEntity member) {
        SpaceMembershipEntity membership = membershipResolver.requireActive(spaceId, callerId);
        if (membership.getRole() == MembershipRole.CUSTOMER) {
            throw new BusinessException("Customers cannot access occupancy data", HttpStatus.FORBIDDEN);
        }
        if (membership.getRole() == MembershipRole.TENANT) {
            if (member.getUser() == null || !member.getUser().getId().equals(callerId)) {
                throw new BusinessException(
                        "OWN_SCOPE_ONLY", "You can only view your own occupancy", HttpStatus.FORBIDDEN);
            }
            return;
        }
        if (!VIEW_MEMBER_OCCUPANCY_ROLES.contains(membership.getRole())) {
            throw new BusinessException("You do not have permission to view occupancies", HttpStatus.FORBIDDEN);
        }
    }

    public void assertSubjectIsResident(MemberEntity member) {
        if (member.getRole() == MembershipRole.OWNER || member.getRole() == MembershipRole.MANAGER) {
            throw new BusinessException(
                    "Owner and manager members cannot be allocated to accommodation", HttpStatus.FORBIDDEN);
        }
    }
}
