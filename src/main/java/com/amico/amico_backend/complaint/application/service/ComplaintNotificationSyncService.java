package com.amico.amico_backend.complaint.application.service;

import com.amico.amico_backend.complaint.domain.model.ComplaintPriority;
import com.amico.amico_backend.complaint.domain.model.ComplaintStatus;
import com.amico.amico_backend.complaint.infrastructure.persistence.entity.SpaceComplaintEntity;
import com.amico.amico_backend.complaint.infrastructure.persistence.repository.SpaceComplaintRepository;
import com.amico.amico_backend.member.domain.model.MembershipRole;
import com.amico.amico_backend.member.domain.model.MembershipStatus;
import com.amico.amico_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.amico.amico_backend.notification.application.port.in.PublishNotificationCommand;
import com.amico.amico_backend.notification.application.service.NotificationService;
import com.amico.amico_backend.notification.domain.model.NotificationCategory;
import com.amico.amico_backend.notification.domain.model.NotificationEntityType;
import com.amico.amico_backend.notification.domain.model.NotificationPriority;
import com.amico.amico_backend.notification.domain.model.NotificationType;
import com.amico.amico_backend.notification.infrastructure.persistence.entity.SpaceNotificationEntity;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps complaint actionable notifications aligned with complaint status.
 * Pending Actions sync from the complaint store (same pattern as payments).
 */
@Service
@RequiredArgsConstructor
public class ComplaintNotificationSyncService {

    private static final Set<ComplaintStatus> OPEN_ACTION_STATUSES =
            EnumSet.of(ComplaintStatus.OPEN, ComplaintStatus.IN_PROGRESS);

    private final SpaceComplaintRepository complaintRepository;
    private final SpaceMembershipRepository membershipRepository;
    private final NotificationService notificationService;

    @Transactional
    public void syncSpace(UUID spaceId) {
        List<SpaceComplaintEntity> open =
                complaintRepository.findBySpace_IdAndStatusInOrderByCreatedAtDesc(
                        spaceId, OPEN_ACTION_STATUSES);
        List<UUID> managerIds = managerUserIds(spaceId);
        Set<String> expectedKeys = new HashSet<>();

        for (SpaceComplaintEntity complaint : open) {
            for (UUID managerId : managerIds) {
                String key = actionDedupeKey(NotificationType.COMPLAINT_PENDING, complaint.getId(), managerId);
                expectedKeys.add(key);
                publishManagerAction(spaceId, managerId, complaint, key);
            }
        }

        resolveStaleManagerActions(spaceId, managerIds, expectedKeys);
    }

    @Transactional
    public void onComplaintCreated(SpaceComplaintEntity complaint) {
        UUID spaceId = complaint.getSpace().getId();
        UUID creatorId = complaint.getCreatedByUserId();
        for (UUID managerId : managerUserIds(spaceId)) {
            String key = actionDedupeKey(NotificationType.COMPLAINT_PENDING, complaint.getId(), managerId);
            publishManagerAction(spaceId, managerId, complaint, key);
            // Informational row so the owner inbox / Global Recent Activity also show the raise.
            // Pending Actions continue to use COMPLAINT_PENDING (ACTION_REQUIRED) only.
            notificationService.publish(PublishNotificationCommand.builder()
                    .spaceId(spaceId)
                    .userId(managerId)
                    .actorId(creatorId)
                    .entityType(NotificationEntityType.COMPLAINT)
                    .entityId(complaint.getId())
                    .notificationType(NotificationType.COMPLAINT_CREATED)
                    .category(NotificationCategory.INFORMATION)
                    .priority(mapPriority(complaint.getPriority()))
                    .title("New complaint")
                    .message(complaint.getTitle())
                    .actionLabel("View Complaint")
                    .actionRoute("ComplaintDetail")
                    .dedupeKey(infoDedupeKey(NotificationType.COMPLAINT_CREATED, complaint.getId(), managerId))
                    .build());
        }
    }

    @Transactional
    public void onComplaintStatusChanged(SpaceComplaintEntity complaint) {
        UUID spaceId = complaint.getSpace().getId();
        UUID complaintId = complaint.getId();

        if (OPEN_ACTION_STATUSES.contains(complaint.getStatus())) {
            for (UUID managerId : managerUserIds(spaceId)) {
                String key = actionDedupeKey(NotificationType.COMPLAINT_PENDING, complaintId, managerId);
                publishManagerAction(spaceId, managerId, complaint, key);
            }
        } else {
            notificationService.resolveOpenForEntity(
                    spaceId,
                    NotificationEntityType.COMPLAINT,
                    complaintId,
                    NotificationType.COMPLAINT_PENDING);
        }

        if (complaint.getStatus() == ComplaintStatus.RESOLVED
                || complaint.getStatus() == ComplaintStatus.CLOSED) {
            UUID creatorId = complaint.getCreatedByUserId();
            if (creatorId != null) {
                notificationService.publish(PublishNotificationCommand.builder()
                        .spaceId(spaceId)
                        .userId(creatorId)
                        .actorId(complaint.getResolvedByUserId())
                        .entityType(NotificationEntityType.COMPLAINT)
                        .entityId(complaintId)
                        .notificationType(NotificationType.COMPLAINT_RESOLVED)
                        .category(NotificationCategory.SUCCESS)
                        .priority(NotificationPriority.MEDIUM)
                        .title("Complaint resolved")
                        .message("\"" + complaint.getTitle() + "\" was marked "
                                + complaint.getStatus().name().toLowerCase().replace('_', ' '))
                        .actionLabel("View Complaint")
                        .actionRoute("ComplaintDetail")
                        .dedupeKey(infoDedupeKey(NotificationType.COMPLAINT_RESOLVED, complaintId, creatorId))
                        .build());
            }
        }
    }

    @Transactional
    public void onComplaintReopened(SpaceComplaintEntity complaint) {
        onComplaintCreated(complaint);
        UUID creatorId = complaint.getCreatedByUserId();
        if (creatorId != null) {
            notificationService.publish(PublishNotificationCommand.builder()
                    .spaceId(complaint.getSpace().getId())
                    .userId(creatorId)
                    .entityType(NotificationEntityType.COMPLAINT)
                    .entityId(complaint.getId())
                    .notificationType(NotificationType.COMPLAINT_CREATED)
                    .category(NotificationCategory.INFORMATION)
                    .priority(NotificationPriority.MEDIUM)
                    .title("Complaint reopened")
                    .message("\"" + complaint.getTitle() + "\" was reopened")
                    .actionLabel("View Complaint")
                    .actionRoute("ComplaintDetail")
                    .dedupeKey(infoDedupeKey(
                                    NotificationType.COMPLAINT_CREATED, complaint.getId(), creatorId)
                            + ":reopen")
                    .build());
        }
    }

    @Transactional
    public void onComplaintAssigned(SpaceComplaintEntity complaint) {
        onComplaintStatusChanged(complaint);
        if (complaint.getAssignedToMembership() != null
                && complaint.getAssignedToMembership().getUser() != null) {
            UUID assigneeUserId = complaint.getAssignedToMembership().getUser().getId();
            notificationService.publish(PublishNotificationCommand.builder()
                    .spaceId(complaint.getSpace().getId())
                    .userId(assigneeUserId)
                    .entityType(NotificationEntityType.COMPLAINT)
                    .entityId(complaint.getId())
                    .notificationType(NotificationType.COMPLAINT_PENDING)
                    .category(NotificationCategory.ACTION_REQUIRED)
                    .priority(NotificationPriority.HIGH)
                    .title("Complaint assigned to you")
                    .message(complaint.getTitle())
                    .actionLabel("View Complaint")
                    .actionRoute("ComplaintDetail")
                    .dedupeKey(actionDedupeKey(
                            NotificationType.COMPLAINT_PENDING, complaint.getId(), assigneeUserId))
                    .build());
        }
    }

    @Transactional
    public void onComplaintCommented(SpaceComplaintEntity complaint, UUID commenterId) {
        UUID spaceId = complaint.getSpace().getId();
        UUID creatorId = complaint.getCreatedByUserId();
        if (creatorId != null && !creatorId.equals(commenterId)) {
            notificationService.publish(PublishNotificationCommand.builder()
                    .spaceId(spaceId)
                    .userId(creatorId)
                    .actorId(commenterId)
                    .entityType(NotificationEntityType.COMPLAINT)
                    .entityId(complaint.getId())
                    .notificationType(NotificationType.COMPLAINT_COMMENTED)
                    .category(NotificationCategory.INFORMATION)
                    .priority(NotificationPriority.LOW)
                    .title("New comment on your complaint")
                    .message(complaint.getTitle())
                    .actionLabel("View Complaint")
                    .actionRoute("ComplaintDetail")
                    .dedupeKey("INFO:COMPLAINT_COMMENTED:" + complaint.getId() + ":" + creatorId + ":"
                            + System.currentTimeMillis() / 60_000)
                    .build());
        }
        if (creatorId != null && creatorId.equals(commenterId)) {
            for (UUID managerId : managerUserIds(spaceId)) {
                notificationService.publish(PublishNotificationCommand.builder()
                        .spaceId(spaceId)
                        .userId(managerId)
                        .actorId(commenterId)
                        .entityType(NotificationEntityType.COMPLAINT)
                        .entityId(complaint.getId())
                        .notificationType(NotificationType.COMPLAINT_COMMENTED)
                        .category(NotificationCategory.INFORMATION)
                        .priority(NotificationPriority.LOW)
                        .title("New comment on complaint")
                        .message(complaint.getTitle())
                        .actionLabel("View Complaint")
                        .actionRoute("ComplaintDetail")
                        .dedupeKey("INFO:COMPLAINT_COMMENTED:" + complaint.getId() + ":" + managerId + ":"
                                + System.currentTimeMillis() / 60_000)
                        .build());
            }
        }
    }

    private void resolveStaleManagerActions(
            UUID spaceId, List<UUID> managerIds, Set<String> expectedDedupeKeys) {
        for (UUID managerId : managerIds) {
            for (SpaceNotificationEntity open : notificationService.listOpenActions(spaceId, managerId)) {
                if (open.getNotificationType() != NotificationType.COMPLAINT_PENDING) {
                    continue;
                }
                if (open.getEntityType() != NotificationEntityType.COMPLAINT) {
                    continue;
                }
                if (!expectedDedupeKeys.contains(open.getDedupeKey())) {
                    // Resolve only this manager's row — never wipe assignee (STAFF) actions.
                    notificationService.resolveOpenForEntityUsers(
                            spaceId,
                            NotificationEntityType.COMPLAINT,
                            open.getEntityId(),
                            NotificationType.COMPLAINT_PENDING,
                            List.of(managerId));
                }
            }
        }
    }

    private void publishManagerAction(
            UUID spaceId, UUID managerId, SpaceComplaintEntity complaint, String dedupeKey) {
        notificationService.publish(PublishNotificationCommand.builder()
                .spaceId(spaceId)
                .userId(managerId)
                .entityType(NotificationEntityType.COMPLAINT)
                .entityId(complaint.getId())
                .notificationType(NotificationType.COMPLAINT_PENDING)
                .category(NotificationCategory.ACTION_REQUIRED)
                .priority(mapPriority(complaint.getPriority()))
                .title("Complaint pending")
                .message(complaint.getTitle())
                .actionLabel("View Complaint")
                .actionRoute("ComplaintDetail")
                .dedupeKey(dedupeKey)
                .build());
    }

    private static NotificationPriority mapPriority(ComplaintPriority priority) {
        if (priority == null) {
            return NotificationPriority.HIGH;
        }
        return switch (priority) {
            case URGENT -> NotificationPriority.CRITICAL;
            case HIGH -> NotificationPriority.HIGH;
            case MEDIUM -> NotificationPriority.MEDIUM;
            case LOW -> NotificationPriority.LOW;
        };
    }

    private List<UUID> managerUserIds(UUID spaceId) {
        return membershipRepository.findBySpaceIdAndStatus(spaceId, MembershipStatus.ACTIVE).stream()
                .filter(m -> m.getRole() == MembershipRole.OWNER || m.getRole() == MembershipRole.MANAGER)
                .map(m -> m.getUser().getId())
                .distinct()
                .toList();
    }

    private static String actionDedupeKey(NotificationType type, UUID entityId, UUID userId) {
        return type.name() + ":" + entityId + ":" + userId;
    }

    private static String infoDedupeKey(NotificationType type, UUID entityId, UUID userId) {
        return "INFO:" + type.name() + ":" + entityId + ":" + userId;
    }
}
