package com.acomi.acomi_backend.enquiry.application.service;

import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Spaces whose enquiries the caller may see because they own or manage the listing.
 * Tenants, customers, and staff are not listing managers.
 */
@Component
@RequiredArgsConstructor
public class EnquiryInboxAccess {

    public static final List<MembershipRole> LISTING_MANAGER_ROLES =
            List.of(MembershipRole.OWNER, MembershipRole.MANAGER);

    private final SpaceMembershipRepository membershipRepository;

    public boolean managesListings(UUID userId) {
        return !authorizedSpaceIds(userId).isEmpty();
    }

    public List<UUID> authorizedSpaceIds(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        List<UUID> ids = membershipRepository.findActiveSpaceIdsForRoles(userId, LISTING_MANAGER_ROLES);
        return ids == null ? List.of() : List.copyOf(ids);
    }

    public boolean canReceive(UUID userId, UUID spaceId) {
        return spaceId != null && authorizedSpaceIds(userId).contains(spaceId);
    }

    public List<UserEntity> managersOf(UUID spaceId) {
        if (spaceId == null) {
            return List.of();
        }
        return membershipRepository.findActiveMembers(spaceId).stream()
                .filter(member -> isListingManager(member.getRole()))
                .map(SpaceMembershipEntity::getUser)
                .filter(user -> user != null && user.isActive())
                .toList();
    }

    public static boolean isListingManager(MembershipRole role) {
        return role == MembershipRole.OWNER || role == MembershipRole.MANAGER;
    }

    public Collection<UUID> requireScope(UUID userId, UUID requestedSpaceId) {
        List<UUID> authorized = authorizedSpaceIds(userId);
        if (requestedSpaceId == null) {
            return authorized;
        }
        if (!authorized.contains(requestedSpaceId)) {
            throw new com.acomi.acomi_backend.common.exception.BusinessException(
                    "ENQUIRY_FORBIDDEN",
                    "You cannot view enquiries for this listing.",
                    org.springframework.http.HttpStatus.FORBIDDEN);
        }
        return List.of(requestedSpaceId);
    }
}
