package com.amico.amico_backend.complaint.application.service;

import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.complaint.infrastructure.persistence.entity.SpaceComplaintEntity;
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
public class SpaceComplaintAccessService {

    private static final List<MembershipRole> MANAGE_COMPLAINTS_ROLES =
            List.of(MembershipRole.OWNER, MembershipRole.MANAGER);

    private static final List<MembershipRole> VIEW_ALL_COMPLAINTS_ROLES =
            List.of(MembershipRole.OWNER, MembershipRole.MANAGER);

    private static final List<MembershipRole> RAISE_COMPLAINT_ROLES = List.of(
            MembershipRole.OWNER,
            MembershipRole.MANAGER,
            MembershipRole.TENANT,
            MembershipRole.CUSTOMER);

    private final SpaceMembershipResolver membershipResolver;
    private final MemberRepository memberRepository;

    public SpaceMembershipEntity requireActiveMembership(UUID spaceId, UUID callerId) {
        return membershipResolver.requireActive(spaceId, callerId);
    }

    public SpaceMembershipEntity requireRaiseComplaint(UUID spaceId, UUID callerId) {
        SpaceMembershipEntity membership = membershipResolver.requireActive(spaceId, callerId);
        if (!RAISE_COMPLAINT_ROLES.contains(membership.getRole())) {
            throw new BusinessException(
                    "STAFF cannot raise complaints in MVP", HttpStatus.FORBIDDEN);
        }
        return membership;
    }

    public SpaceMembershipEntity requireManageComplaints(UUID spaceId, UUID callerId) {
        SpaceMembershipEntity membership = membershipResolver.requireActive(spaceId, callerId);
        if (!MANAGE_COMPLAINTS_ROLES.contains(membership.getRole())) {
            throw new BusinessException(
                    "Only OWNER or MANAGER can manage complaints", HttpStatus.FORBIDDEN);
        }
        return membership;
    }

    public boolean canManageComplaints(SpaceMembershipEntity membership) {
        return MANAGE_COMPLAINTS_ROLES.contains(membership.getRole());
    }

    public boolean canViewAllComplaints(SpaceMembershipEntity membership) {
        return VIEW_ALL_COMPLAINTS_ROLES.contains(membership.getRole());
    }

    public boolean isOwnScopeOnly(SpaceMembershipEntity membership) {
        MembershipRole role = membership.getRole();
        return role == MembershipRole.TENANT || role == MembershipRole.CUSTOMER;
    }

    public boolean isStaff(SpaceMembershipEntity membership) {
        return membership.getRole() == MembershipRole.STAFF;
    }

    public UUID resolveOwnMemberId(UUID spaceId, UUID callerId) {
        return memberRepository
                .findActiveBySpaceIdAndUserId(spaceId, callerId)
                .map(MemberEntity::getId)
                .orElseThrow(() -> new BusinessException(
                        "No member profile linked to your account", HttpStatus.FORBIDDEN));
    }

    public MemberEntity requireOwnMember(UUID spaceId, UUID callerId) {
        return memberRepository
                .findActiveBySpaceIdAndUserId(spaceId, callerId)
                .orElseThrow(() -> new BusinessException(
                        "No member profile linked to your account", HttpStatus.FORBIDDEN));
    }

    public void requireViewComplaint(
            SpaceMembershipEntity membership, SpaceComplaintEntity complaint, UUID callerId) {
        if (canViewAllComplaints(membership)) {
            return;
        }
        if (isStaff(membership)) {
            if (isAssignedTo(membership, complaint) || isCreator(complaint, callerId)) {
                return;
            }
            throw new BusinessException(
                    "STAFF can only view assigned complaints", HttpStatus.FORBIDDEN);
        }
        if (isOwnScopeOnly(membership)) {
            if (!isCreator(complaint, callerId)) {
                throw new BusinessException(
                        "OWN_SCOPE_ONLY",
                        "You can only view your own complaints",
                        HttpStatus.FORBIDDEN);
            }
            return;
        }
        throw new BusinessException(
                "You do not have permission to view complaints", HttpStatus.FORBIDDEN);
    }

    public void requireInternalNote(SpaceMembershipEntity membership) {
        if (!canManageComplaints(membership) && !isStaff(membership)) {
            throw new BusinessException(
                    "Only operators can add internal notes", HttpStatus.FORBIDDEN);
        }
    }

    public boolean canSeeInternalNotes(SpaceMembershipEntity membership) {
        return canManageComplaints(membership) || isStaff(membership);
    }

    private static boolean isCreator(SpaceComplaintEntity complaint, UUID callerId) {
        return complaint.getCreatedByUserId() != null
                && complaint.getCreatedByUserId().equals(callerId);
    }

    private static boolean isAssignedTo(
            SpaceMembershipEntity membership, SpaceComplaintEntity complaint) {
        return complaint.getAssignedToMembership() != null
                && complaint.getAssignedToMembership().getId().equals(membership.getId());
    }
}
