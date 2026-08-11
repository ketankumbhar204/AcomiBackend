package com.amico.amico_backend.payment.application.service;

import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.member.application.service.SpaceMembershipResolver;
import com.amico.amico_backend.member.domain.model.MembershipRole;
import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.amico.amico_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.amico.amico_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.amico.amico_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SpacePaymentAccessService {

    private static final List<MembershipRole> MANAGE_PAYMENTS_ROLES =
            List.of(MembershipRole.OWNER, MembershipRole.MANAGER);

    private static final List<MembershipRole> VIEW_ALL_PAYMENTS_ROLES =
            List.of(MembershipRole.OWNER, MembershipRole.MANAGER, MembershipRole.STAFF);

    private final SpaceMembershipResolver membershipResolver;
    private final MemberRepository memberRepository;

    public SpaceMembershipEntity requireActiveMembership(UUID spaceId, UUID callerId) {
        return membershipResolver.requireActive(spaceId, callerId);
    }

    public SpaceMembershipEntity requireManagePayments(UUID spaceId, UUID callerId) {
        SpaceMembershipEntity membership = membershipResolver.requireActive(spaceId, callerId);
        if (!MANAGE_PAYMENTS_ROLES.contains(membership.getRole())) {
            throw new BusinessException(
                    "Only OWNER or MANAGER can review payments", HttpStatus.FORBIDDEN);
        }
        return membership;
    }

    public boolean canViewAllPayments(SpaceMembershipEntity membership) {
        return VIEW_ALL_PAYMENTS_ROLES.contains(membership.getRole());
    }

    public boolean isOwnScopeOnly(SpaceMembershipEntity membership) {
        MembershipRole role = membership.getRole();
        return role == MembershipRole.TENANT || role == MembershipRole.CUSTOMER;
    }

    public UUID resolveOwnMemberId(UUID spaceId, UUID callerId) {
        return memberRepository
                .findActiveBySpaceIdAndUserId(spaceId, callerId)
                .map(MemberEntity::getId)
                .orElseThrow(() -> new BusinessException(
                        "No member profile linked to your account", HttpStatus.FORBIDDEN));
    }

    public void requireViewPayment(
            SpaceMembershipEntity membership, SpacePaymentEntity payment, UUID callerId) {
        if (canViewAllPayments(membership)) {
            return;
        }
        if (isOwnScopeOnly(membership)) {
            MemberEntity member = payment.getMember();
            if (member.getUser() == null || !member.getUser().getId().equals(callerId)) {
                throw new BusinessException(
                        "OWN_SCOPE_ONLY", "You can only view your own payments", HttpStatus.FORBIDDEN);
            }
            return;
        }
        throw new BusinessException("You do not have permission to view payments", HttpStatus.FORBIDDEN);
    }

    public void requireSubmitProof(
            SpaceMembershipEntity membership, SpacePaymentEntity payment, UUID callerId) {
        if (!isOwnScopeOnly(membership)) {
            throw new BusinessException(
                    "Only tenants or customers can submit payment proof", HttpStatus.FORBIDDEN);
        }
        MemberEntity member = payment.getMember();
        if (member.getUser() == null || !member.getUser().getId().equals(callerId)) {
            throw new BusinessException(
                    "OWN_SCOPE_ONLY", "You can only submit proof for your own payments", HttpStatus.FORBIDDEN);
        }
    }
}
