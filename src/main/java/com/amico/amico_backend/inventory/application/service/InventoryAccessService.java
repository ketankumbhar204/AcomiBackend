package com.amico.amico_backend.inventory.application.service;

import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.member.application.service.SpaceMembershipResolver;
import com.amico.amico_backend.member.domain.model.MembershipRole;
import com.amico.amico_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryAccessService {

    private static final List<MembershipRole> VIEW_ROLES =
            List.of(MembershipRole.OWNER, MembershipRole.MANAGER, MembershipRole.STAFF);

    private static final List<MembershipRole> MANAGE_ROLES =
            List.of(MembershipRole.OWNER, MembershipRole.MANAGER);

    private final SpaceMembershipResolver membershipResolver;

    public SpaceMembershipEntity requireViewInventory(UUID spaceId, UUID callerId) {
        SpaceMembershipEntity membership = membershipResolver.requireActive(spaceId, callerId);
        if (!VIEW_ROLES.contains(membership.getRole())) {
            throw new BusinessException("You do not have permission to view inventory", HttpStatus.FORBIDDEN);
        }
        return membership;
    }

    public SpaceMembershipEntity requireManageInventory(UUID spaceId, UUID callerId) {
        SpaceMembershipEntity membership = membershipResolver.requireActive(spaceId, callerId);
        if (!MANAGE_ROLES.contains(membership.getRole())) {
            throw new BusinessException(
                    "Only OWNER or MANAGER can manage inventory", HttpStatus.FORBIDDEN);
        }
        return membership;
    }
}
