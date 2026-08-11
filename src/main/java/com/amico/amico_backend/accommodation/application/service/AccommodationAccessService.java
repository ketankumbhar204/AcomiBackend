package com.amico.amico_backend.accommodation.application.service;

import com.amico.amico_backend.accommodation.domain.model.AccommodationProfile;
import com.amico.amico_backend.accommodation.domain.policy.AccommodationProfileResolver;
import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.common.exception.ResourceNotFoundException;
import com.amico.amico_backend.member.application.service.SpaceMembershipResolver;
import com.amico.amico_backend.member.domain.model.MembershipRole;
import com.amico.amico_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.amico.amico_backend.space.domain.model.SpaceType;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.amico.amico_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccommodationAccessService {

    private static final List<MembershipRole> VIEW_STRUCTURE_ROLES =
            List.of(MembershipRole.OWNER, MembershipRole.MANAGER, MembershipRole.STAFF);

    private static final List<MembershipRole> MANAGE_STRUCTURE_ROLES =
            List.of(MembershipRole.OWNER, MembershipRole.MANAGER);

    private final SpaceRepository spaceRepository;
    private final SpaceMembershipResolver membershipResolver;
    private final AccommodationProfileResolver profileResolver;

    public SpaceEntity loadAccommodationSpace(UUID spaceId) {
        SpaceEntity space = spaceRepository.findByIdAndIsActiveTrue(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));
        if (!profileResolver.isAccommodationApplicable(space.getType())) {
            throw new BusinessException(
                    "Accommodation is not applicable for Mess spaces", HttpStatus.FORBIDDEN);
        }
        return space;
    }

    public AccommodationProfile loadProfile(UUID spaceId) {
        SpaceEntity space = loadAccommodationSpace(spaceId);
        return profileResolver.resolve(space.getType());
    }

    public SpaceType loadSpaceType(UUID spaceId) {
        return loadAccommodationSpace(spaceId).getType();
    }

    public SpaceMembershipEntity assertCanViewStructure(UUID spaceId, UUID callerId) {
        SpaceMembershipEntity membership = membershipResolver.requireActive(spaceId, callerId);
        loadAccommodationSpace(spaceId);
        requireViewStructure(membership);
        return membership;
    }

    public SpaceMembershipEntity assertCanManageStructure(UUID spaceId, UUID callerId) {
        SpaceMembershipEntity membership = membershipResolver.requireActive(spaceId, callerId);
        loadAccommodationSpace(spaceId);
        requireManageStructure(membership);
        return membership;
    }

    public SpaceMembershipEntity assertCanDeactivateStructure(UUID spaceId, UUID callerId) {
        SpaceMembershipEntity membership = membershipResolver.requireActive(spaceId, callerId);
        loadAccommodationSpace(spaceId);
        requireDeactivateStructure(membership);
        return membership;
    }

    public SpaceMembershipEntity assertCanSearchAllocationTargets(UUID spaceId, UUID callerId) {
        return assertCanManageStructure(spaceId, callerId);
    }

    public void requireViewStructure(SpaceMembershipEntity membership) {
        if (!VIEW_STRUCTURE_ROLES.contains(membership.getRole())) {
            throw new BusinessException(
                    "You do not have permission to view accommodation structure", HttpStatus.FORBIDDEN);
        }
    }

    public void requireManageStructure(SpaceMembershipEntity membership) {
        if (!MANAGE_STRUCTURE_ROLES.contains(membership.getRole())) {
            throw new BusinessException(
                    "Only OWNER or MANAGER can perform this action", HttpStatus.FORBIDDEN);
        }
    }

    public void requireDeactivateStructure(SpaceMembershipEntity membership) {
        if (membership.getRole() != MembershipRole.OWNER) {
            throw new BusinessException(
                    "Only the space owner can perform this action", HttpStatus.FORBIDDEN);
        }
    }
}
