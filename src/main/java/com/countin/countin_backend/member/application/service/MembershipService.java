package com.countin.countin_backend.member.application.service;

import com.countin.countin_backend.common.exception.BusinessException;
import com.countin.countin_backend.common.exception.ResourceNotFoundException;
import com.countin.countin_backend.member.api.dto.response.PendingInvitationResponse;
import com.countin.countin_backend.member.domain.model.InvitationStatus;
import com.countin.countin_backend.member.domain.model.MembershipRole;
import com.countin.countin_backend.member.domain.model.MembershipStatus;
import com.countin.countin_backend.member.infrastructure.persistence.entity.InvitationEntity;
import com.countin.countin_backend.member.infrastructure.persistence.repository.InvitationRepository;
import com.countin.countin_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.countin.countin_backend.notification.application.service.InvitationNotificationSyncService;
import com.countin.countin_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipService {

    private final SpaceRepository spaceRepository;
    private final SpaceMembershipRepository spaceMembershipRepository;
    private final InvitationRepository invitationRepository;
    private final InvitationNotificationSyncService invitationNotificationSyncService;

    @Transactional(readOnly = true)
    public List<PendingInvitationResponse> getPendingInvitations(UUID spaceId, UUID callerId) {
        log.info("Viewing invitations: spaceId={}, callerId={}", spaceId, callerId);

        assertSpaceExists(spaceId);
        assertCallerBelongsToSpace(spaceId, callerId);

        return invitationRepository.findPendingInvitations(spaceId)
                .stream()
                .map(PendingInvitationResponse::from)
                .toList();
    }

    @Transactional
    public void cancelInvitation(UUID invitationId, UUID callerId) {
        log.info("Cancelling invitation: invitationId={}, callerId={}", invitationId, callerId);

        InvitationEntity invitation = invitationRepository.findPendingInvitation(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", "id", invitationId));

        assertOwnerOrManager(invitation.getSpace().getId(), callerId);

        invitation.setStatus(InvitationStatus.CANCELLED);
        invitationRepository.save(invitation);
        invitationNotificationSyncService.onInvitationCancelledOrExpired(invitation);
    }

    private void assertSpaceExists(UUID spaceId) {
        spaceRepository.findByIdAndIsActiveTrue(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));
    }

    private void assertCallerBelongsToSpace(UUID spaceId, UUID callerId) {
        boolean belongs = spaceMembershipRepository.existsByUserIdAndSpaceIdAndStatus(
                callerId, spaceId, MembershipStatus.ACTIVE);
        if (!belongs) {
            throw new BusinessException(
                    "You do not have access to this space", HttpStatus.FORBIDDEN);
        }
    }

    private void assertOwnerOrManager(UUID spaceId, UUID callerId) {
        boolean allowed = spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                callerId, spaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER));
        if (!allowed) {
            throw new BusinessException(
                    "Only OWNER or MANAGER can perform this action", HttpStatus.FORBIDDEN);
        }
    }
}
