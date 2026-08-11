package com.amico.amico_backend.meal.application.service;

import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.member.application.service.SpaceMembershipResolver;
import com.amico.amico_backend.member.domain.model.MembershipRole;
import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.amico.amico_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.amico.amico_backend.member.infrastructure.persistence.repository.MemberRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MealAccessService {

    private static final List<MembershipRole> MANAGE_MEALS_ROLES =
            List.of(MembershipRole.OWNER, MembershipRole.MANAGER);

    private static final List<MembershipRole> VIEW_ALL_PARTICIPATIONS_ROLES =
            List.of(MembershipRole.OWNER, MembershipRole.MANAGER, MembershipRole.STAFF);

    private final SpaceMembershipResolver membershipResolver;
    private final MemberRepository memberRepository;

    public SpaceMembershipEntity requireViewMeals(UUID spaceId, UUID callerId) {
        return membershipResolver.requireActive(spaceId, callerId);
    }

    public SpaceMembershipEntity requireManageMeals(UUID spaceId, UUID callerId) {
        SpaceMembershipEntity membership = membershipResolver.requireActive(spaceId, callerId);
        if (!MANAGE_MEALS_ROLES.contains(membership.getRole())) {
            throw new BusinessException("Only OWNER or MANAGER can manage meals", HttpStatus.FORBIDDEN);
        }
        return membership;
    }

    public SpaceMembershipEntity requireManageParticipation(UUID spaceId, UUID callerId) {
        return requireManageMeals(spaceId, callerId);
    }

    public void requireViewParticipation(UUID spaceId, UUID memberId, UUID callerId, MemberEntity member) {
        SpaceMembershipEntity membership = membershipResolver.requireActive(spaceId, callerId);
        if (VIEW_ALL_PARTICIPATIONS_ROLES.contains(membership.getRole())) {
            return;
        }
        if (membership.getRole() == MembershipRole.TENANT || membership.getRole() == MembershipRole.CUSTOMER) {
            if (member.getUser() == null || !member.getUser().getId().equals(callerId)) {
                throw new BusinessException(
                        "OWN_SCOPE_ONLY", "You can only view your own meal participation", HttpStatus.FORBIDDEN);
            }
            return;
        }
        throw new BusinessException("You do not have permission to view meal participation", HttpStatus.FORBIDDEN);
    }

    public boolean canManageMeals(SpaceMembershipEntity membership) {
        return MANAGE_MEALS_ROLES.contains(membership.getRole());
    }

    public boolean isParticipantScopeOnly(SpaceMembershipEntity membership) {
        MembershipRole role = membership.getRole();
        return role == MembershipRole.TENANT || role == MembershipRole.CUSTOMER;
    }

    public UUID resolveOwnMemberId(UUID spaceId, UUID callerId) {
        return memberRepository
                .findActiveBySpaceIdAndUserId(spaceId, callerId)
                .map(MemberEntity::getId)
                .orElseThrow(() -> new BusinessException("No member profile linked to your account", HttpStatus.FORBIDDEN));
    }
}
