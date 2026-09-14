package com.acomi.acomi_backend.notification.application.service;

import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.domain.model.MembershipStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand;
import com.acomi.acomi_backend.notification.domain.model.NotificationCategory;
import com.acomi.acomi_backend.notification.domain.model.NotificationEntityType;
import com.acomi.acomi_backend.notification.domain.model.NotificationPriority;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Member/space lifecycle informational notifications. Recipients always come from
 * persisted memberships — never from client-supplied user IDs.
 */
@Service
@RequiredArgsConstructor
public class MembershipNotificationSyncService {

    private final NotificationService notificationService;
    private final SpaceMembershipRepository membershipRepository;

    @Transactional
    public void onMemberRemoved(MemberEntity member) {
        UUID userId = linkedUserId(member);
        if (userId == null || member.getSpace() == null) {
            return;
        }
        UUID spaceId = member.getSpace().getId();
        String spaceName = spaceName(member.getSpace());
        notificationService.publish(PublishNotificationCommand.builder()
                .spaceId(spaceId)
                .userId(userId)
                .entityType(NotificationEntityType.MEMBER)
                .entityId(member.getId())
                .notificationType(NotificationType.MEMBERSHIP_REMOVED)
                .category(NotificationCategory.WARNING)
                .priority(NotificationPriority.HIGH)
                .title("Removed from space")
                .message("You are no longer a member of " + spaceName + ".")
                .actionLabel("View Spaces")
                .actionRoute("MySpaces")
                .dedupeKey("INFO:MEMBERSHIP_REMOVED:" + spaceId + ":" + userId)
                .build());
    }

    @Transactional
    public void onMemberStatusChanged(MemberEntity member, String previousStatus, String newStatus) {
        UUID userId = linkedUserId(member);
        if (userId == null || member.getSpace() == null) {
            return;
        }
        if (previousStatus != null && previousStatus.equals(newStatus)) {
            return;
        }
        UUID spaceId = member.getSpace().getId();
        notificationService.publish(PublishNotificationCommand.builder()
                .spaceId(spaceId)
                .userId(userId)
                .entityType(NotificationEntityType.MEMBER)
                .entityId(member.getId())
                .notificationType(NotificationType.MEMBERSHIP_ROLE_CHANGED)
                .category(NotificationCategory.INFORMATION)
                .priority(NotificationPriority.MEDIUM)
                .title("Membership updated")
                .message("Your status in " + spaceName(member.getSpace()) + " is now " + newStatus + ".")
                .actionLabel("View Space")
                .actionRoute("Dashboard")
                .dedupeKey("INFO:MEMBER_STATUS:" + member.getId() + ":" + newStatus)
                .build());
    }

    @Transactional
    public void onMemberRoleChanged(MemberEntity member, MembershipRole previousRole, MembershipRole newRole) {
        UUID userId = linkedUserId(member);
        if (userId == null || member.getSpace() == null || previousRole == null || previousRole == newRole) {
            return;
        }
        UUID spaceId = member.getSpace().getId();
        notificationService.publish(PublishNotificationCommand.builder()
                .spaceId(spaceId)
                .userId(userId)
                .entityType(NotificationEntityType.MEMBER)
                .entityId(member.getId())
                .notificationType(NotificationType.MEMBERSHIP_ROLE_CHANGED)
                .category(NotificationCategory.INFORMATION)
                .priority(NotificationPriority.MEDIUM)
                .title("Your role changed")
                .message("Your role in " + spaceName(member.getSpace()) + " is now " + newRole + ".")
                .actionLabel("View Space")
                .actionRoute("Dashboard")
                .dedupeKey("INFO:MEMBER_ROLE:" + member.getId() + ":" + newRole)
                .build());
    }

    @Transactional
    public void onSpaceDeactivated(SpaceEntity space, UUID actorId) {
        if (space == null || space.getId() == null) {
            return;
        }
        UUID spaceId = space.getId();
        String spaceName = spaceName(space);
        List<SpaceMembershipEntity> active =
                membershipRepository.findBySpaceIdAndStatus(spaceId, MembershipStatus.ACTIVE);
        Set<UUID> recipients = new LinkedHashSet<>();
        for (SpaceMembershipEntity membership : active) {
            if (membership.getUser() == null) {
                continue;
            }
            UUID userId = membership.getUser().getId();
            if (userId != null) {
                recipients.add(userId);
            }
        }
        for (UUID userId : recipients) {
            if (userId.equals(actorId)) {
                continue;
            }
            notificationService.publish(PublishNotificationCommand.builder()
                    .spaceId(spaceId)
                    .userId(userId)
                    .actorId(actorId)
                    .entityType(NotificationEntityType.SPACE)
                    .entityId(spaceId)
                    .notificationType(NotificationType.SPACE_DEACTIVATED)
                    .category(NotificationCategory.WARNING)
                    .priority(NotificationPriority.HIGH)
                    .title("Space closed")
                    .message(spaceName + " is no longer active.")
                    .actionLabel("View Spaces")
                    .actionRoute("MySpaces")
                    .dedupeKey("INFO:SPACE_DEACTIVATED:" + spaceId + ":" + userId)
                    .build());
        }
    }

    @Transactional
    public void onOwnershipTransferred(SpaceEntity space, UUID previousOwnerId, UUID newOwnerId) {
        if (space == null || previousOwnerId == null || newOwnerId == null || previousOwnerId.equals(newOwnerId)) {
            return;
        }
        UUID spaceId = space.getId();
        String spaceName = spaceName(space);
        notificationService.publish(PublishNotificationCommand.builder()
                .spaceId(spaceId)
                .userId(previousOwnerId)
                .actorId(newOwnerId)
                .entityType(NotificationEntityType.SPACE)
                .entityId(spaceId)
                .notificationType(NotificationType.OWNERSHIP_TRANSFERRED)
                .category(NotificationCategory.WARNING)
                .priority(NotificationPriority.HIGH)
                .title("Ownership transferred")
                .message("You are no longer the owner of " + spaceName + ".")
                .actionLabel("View Spaces")
                .actionRoute("MySpaces")
                .dedupeKey("INFO:OWNERSHIP_TRANSFERRED:FROM:" + spaceId + ":" + previousOwnerId)
                .build());
        notificationService.publish(PublishNotificationCommand.builder()
                .spaceId(spaceId)
                .userId(newOwnerId)
                .actorId(previousOwnerId)
                .entityType(NotificationEntityType.SPACE)
                .entityId(spaceId)
                .notificationType(NotificationType.OWNERSHIP_TRANSFERRED)
                .category(NotificationCategory.SUCCESS)
                .priority(NotificationPriority.HIGH)
                .title("You are the new owner")
                .message("You are now the owner of " + spaceName + ".")
                .actionLabel("Open Space")
                .actionRoute("Dashboard")
                .dedupeKey("INFO:OWNERSHIP_TRANSFERRED:TO:" + spaceId + ":" + newOwnerId)
                .build());
    }

    private static UUID linkedUserId(MemberEntity member) {
        UserEntity user = member.getUser();
        return user == null ? null : user.getId();
    }

    private static String spaceName(SpaceEntity space) {
        if (space == null || space.getName() == null || space.getName().isBlank()) {
            return "your space";
        }
        return space.getName().trim();
    }
}
