package com.acomi.acomi_backend.notification.application.service;

import com.acomi.acomi_backend.member.domain.model.InvitationStatus;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.domain.model.MembershipStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.InvitationEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.InvitationRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand;
import com.acomi.acomi_backend.notification.domain.model.NotificationCategory;
import com.acomi.acomi_backend.notification.domain.model.NotificationEntityType;
import com.acomi.acomi_backend.notification.domain.model.NotificationPriority;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.notification.infrastructure.persistence.entity.SpaceNotificationEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps invitation actionable / informational notifications aligned with the invitation store.
 */
@Service
@RequiredArgsConstructor
public class InvitationNotificationSyncService {

    private final InvitationRepository invitationRepository;
    private final SpaceMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public void syncSpace(UUID spaceId) {
        LocalDateTime now = LocalDateTime.now();
        List<InvitationEntity> pending = invitationRepository.findPendingInvitations(spaceId).stream()
                .filter(inv -> inv.getExpiresAt() == null || inv.getExpiresAt().isAfter(now))
                .toList();
        List<UUID> managerIds = managerUserIds(spaceId);
        Set<String> expectedKeys = new HashSet<>();
        Set<UUID> recipientIds = new HashSet<>(managerIds);

        for (InvitationEntity invitation : pending) {
            for (UUID managerId : managerIds) {
                String key = actionDedupeKey(invitation.getId(), managerId);
                expectedKeys.add(key);
                publishManagerPending(spaceId, managerId, invitation, key);
            }
            Optional<UserEntity> invitee = userRepository.findByMobileNumber(invitation.getMobileNumber());
            if (invitee.isPresent() && invitee.get().isActive()) {
                UUID inviteeId = invitee.get().getId();
                recipientIds.add(inviteeId);
                String key = actionDedupeKey(invitation.getId(), inviteeId);
                expectedKeys.add(key);
                publishInviteePending(spaceId, inviteeId, invitation, key);
            }
        }

        for (UUID recipientId : recipientIds) {
            for (SpaceNotificationEntity open : notificationService.listOpenActions(spaceId, recipientId)) {
                if (open.getNotificationType() != NotificationType.PENDING_INVITATION) {
                    continue;
                }
                if (!expectedKeys.contains(open.getDedupeKey())) {
                    notificationService.resolveOpenForEntity(
                            spaceId,
                            NotificationEntityType.INVITATION,
                            open.getEntityId(),
                            NotificationType.PENDING_INVITATION);
                }
            }
        }
    }

    @Transactional
    public void onInvitationCreated(InvitationEntity invitation) {
        syncSpace(invitation.getSpace().getId());
    }

    @Transactional
    public void onInvitationAccepted(InvitationEntity invitation, UUID acceptedByUserId) {
        UUID spaceId = invitation.getSpace().getId();
        notificationService.resolveOpenForEntity(
                spaceId,
                NotificationEntityType.INVITATION,
                invitation.getId(),
                NotificationType.PENDING_INVITATION);

        for (UUID managerId : managerUserIds(spaceId)) {
            notificationService.publish(PublishNotificationCommand.builder()
                    .spaceId(spaceId)
                    .userId(managerId)
                    .actorId(acceptedByUserId)
                    .entityType(NotificationEntityType.INVITATION)
                    .entityId(invitation.getId())
                    .notificationType(NotificationType.INVITATION_ACCEPTED)
                    .category(NotificationCategory.SUCCESS)
                    .priority(NotificationPriority.MEDIUM)
                    .title("Invitation accepted")
                    .message("A new member joined " + invitation.getSpace().getName())
                    .actionLabel("View Members")
                    .actionRoute("Members")
                    .dedupeKey("INFO:INVITATION_ACCEPTED:" + invitation.getId() + ":" + managerId)
                    .build());
        }
        notificationService.publish(PublishNotificationCommand.builder()
                .spaceId(spaceId)
                .userId(acceptedByUserId)
                .entityType(NotificationEntityType.INVITATION)
                .entityId(invitation.getId())
                .notificationType(NotificationType.MEMBERSHIP_APPROVED)
                .category(NotificationCategory.SUCCESS)
                .priority(NotificationPriority.MEDIUM)
                .title("Membership approved")
                .message("You are now a member of " + invitation.getSpace().getName() + ".")
                .actionLabel("Open Space")
                .actionRoute("Dashboard")
                .dedupeKey("INFO:MEMBERSHIP_APPROVED:" + invitation.getId() + ":" + acceptedByUserId)
                .build());
    }

    @Transactional
    public void onInvitationCancelledOrExpired(InvitationEntity invitation) {
        UUID spaceId = invitation.getSpace().getId();
        notificationService.resolveOpenForEntity(
                spaceId,
                NotificationEntityType.INVITATION,
                invitation.getId(),
                NotificationType.PENDING_INVITATION);
        if (invitation.getStatus() != InvitationStatus.CANCELLED
                && invitation.getStatus() != InvitationStatus.EXPIRED) {
            return;
        }
        userRepository.findByMobileNumber(invitation.getMobileNumber()).ifPresent(invitee -> {
            if (!invitee.isActive()) {
                return;
            }
            boolean expired = invitation.getStatus() == InvitationStatus.EXPIRED;
            NotificationType type = expired
                    ? NotificationType.INVITATION_EXPIRED
                    : NotificationType.MEMBERSHIP_REJECTED;
            String title = expired ? "Invitation expired" : "Invitation withdrawn";
            String message = expired
                    ? "Your invitation to " + invitation.getSpace().getName() + " has expired."
                    : "Your invitation to " + invitation.getSpace().getName() + " is no longer available.";
            notificationService.publish(PublishNotificationCommand.builder()
                    .spaceId(spaceId)
                    .userId(invitee.getId())
                    .entityType(NotificationEntityType.INVITATION)
                    .entityId(invitation.getId())
                    .notificationType(type)
                    .category(NotificationCategory.INFORMATION)
                    .priority(NotificationPriority.MEDIUM)
                    .title(title)
                    .message(message)
                    .actionLabel("View Invitations")
                    .actionRoute("AcceptInvitations")
                    .dedupeKey("INFO:" + type.name() + ":" + invitation.getId() + ":" + invitee.getId())
                    .build());
        });
    }

    private void publishManagerPending(
            UUID spaceId, UUID managerId, InvitationEntity invitation, String dedupeKey) {
        notificationService.publish(PublishNotificationCommand.builder()
                .spaceId(spaceId)
                .userId(managerId)
                .entityType(NotificationEntityType.INVITATION)
                .entityId(invitation.getId())
                .notificationType(NotificationType.PENDING_INVITATION)
                .category(NotificationCategory.ACTION_REQUIRED)
                .priority(NotificationPriority.MEDIUM)
                .title("Pending invitation")
                .message("A new member has been invited to your space")
                .actionLabel("View Invitations")
                .actionRoute("Members")
                .dedupeKey(dedupeKey)
                .build());
    }

    private void publishInviteePending(
            UUID spaceId, UUID inviteeId, InvitationEntity invitation, String dedupeKey) {
        notificationService.publish(PublishNotificationCommand.builder()
                .spaceId(spaceId)
                .userId(inviteeId)
                .entityType(NotificationEntityType.INVITATION)
                .entityId(invitation.getId())
                .notificationType(NotificationType.PENDING_INVITATION)
                .category(NotificationCategory.ACTION_REQUIRED)
                .priority(NotificationPriority.HIGH)
                .title("Space invitation")
                .message("You were invited to join " + invitation.getSpace().getName())
                .actionLabel("Accept Invitation")
                .actionRoute("AcceptInvitations")
                .dedupeKey(dedupeKey)
                .build());
    }

    private List<UUID> managerUserIds(UUID spaceId) {
        return membershipRepository.findBySpaceIdAndStatus(spaceId, MembershipStatus.ACTIVE).stream()
                .filter(m -> m.getRole() == MembershipRole.OWNER || m.getRole() == MembershipRole.MANAGER)
                .map(m -> m.getUser().getId())
                .distinct()
                .toList();
    }

    private static String actionDedupeKey(UUID invitationId, UUID userId) {
        return NotificationType.PENDING_INVITATION.name() + ":" + invitationId + ":" + userId;
    }
}
