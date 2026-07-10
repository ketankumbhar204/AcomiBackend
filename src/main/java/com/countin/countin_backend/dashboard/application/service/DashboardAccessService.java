package com.countin.countin_backend.dashboard.application.service;

import com.countin.countin_backend.common.exception.BusinessException;
import com.countin.countin_backend.member.application.service.SpaceMembershipResolver;
import com.countin.countin_backend.member.domain.model.MembershipRole;
import com.countin.countin_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardAccessService {

    private static final List<MembershipRole> VIEW_DASHBOARD_ROLES =
            List.of(MembershipRole.OWNER, MembershipRole.MANAGER, MembershipRole.STAFF);

    private static final List<MembershipRole> MANAGE_PAYMENTS_ROLES =
            List.of(MembershipRole.OWNER, MembershipRole.MANAGER);

    private final SpaceMembershipResolver membershipResolver;

    public SpaceMembershipEntity requireViewDashboard(UUID spaceId, UUID callerId) {
        SpaceMembershipEntity membership = membershipResolver.requireActive(spaceId, callerId);
        if (!VIEW_DASHBOARD_ROLES.contains(membership.getRole())) {
            throw new BusinessException("You do not have permission to view the dashboard", HttpStatus.FORBIDDEN);
        }
        return membership;
    }

    public SpaceMembershipEntity requireManagePayments(UUID spaceId, UUID callerId) {
        SpaceMembershipEntity membership = membershipResolver.requireActive(spaceId, callerId);
        if (!MANAGE_PAYMENTS_ROLES.contains(membership.getRole())) {
            throw new BusinessException("Only OWNER or MANAGER can view payment summaries", HttpStatus.FORBIDDEN);
        }
        return membership;
    }

    /** Any active space member (owner, manager, staff, tenant, customer). */
    public SpaceMembershipEntity requireActiveMembership(UUID spaceId, UUID callerId) {
        return membershipResolver.requireActive(spaceId, callerId);
    }

    public boolean canManagePayments(UUID spaceId, UUID callerId) {
        SpaceMembershipEntity membership = membershipResolver.requireActive(spaceId, callerId);
        return MANAGE_PAYMENTS_ROLES.contains(membership.getRole());
    }
}
