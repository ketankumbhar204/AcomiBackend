package com.acomi.acomi_backend.notification.application.service;

import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.domain.model.MembershipStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand;
import com.acomi.acomi_backend.notification.domain.model.NotificationCategory;
import com.acomi.acomi_backend.notification.domain.model.NotificationEntityType;
import com.acomi.acomi_backend.notification.domain.model.NotificationPriority;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.notification.infrastructure.persistence.entity.SpaceNotificationEntity;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Syncs same-day occupancy actionable notifications from the occupancy store
 * (reservations starting / move-ins / move-outs scheduled today).
 */
@Service
@RequiredArgsConstructor
public class OccupancyNotificationSyncService {

    private static final Set<NotificationType> OCCUPANCY_ACTION_TYPES = EnumSet.of(
            NotificationType.MOVE_IN_SCHEDULED_TODAY,
            NotificationType.MOVE_OUT_SCHEDULED_TODAY,
            NotificationType.RESERVATION_STARTING_TODAY);

    private final OccupancyRepository occupancyRepository;
    private final SpaceMembershipRepository membershipRepository;
    private final NotificationService notificationService;

    @Transactional
    public void syncSpace(UUID spaceId) {
        LocalDate today = LocalDate.now();
        List<UUID> managerIds = managerUserIds(spaceId);
        if (managerIds.isEmpty()) {
            return;
        }

        List<OccupancyEntity> startingReservations =
                occupancyRepository.findBySpaceIdAndStatusAndMoveInDate(spaceId, OccupancyStatus.RESERVED, today);
        List<OccupancyEntity> moveOutsToday =
                occupancyRepository.findBySpaceIdAndStatusAndExpectedExitDate(
                        spaceId, OccupancyStatus.ACTIVE, today);

        Set<String> expectedKeys = new HashSet<>();

        for (OccupancyEntity occupancy : startingReservations) {
            for (UUID managerId : managerIds) {
                String key = actionDedupeKey(
                        NotificationType.MOVE_IN_SCHEDULED_TODAY, occupancy.getId(), managerId);
                expectedKeys.add(key);
                publishAction(
                        spaceId,
                        managerId,
                        occupancy,
                        NotificationType.MOVE_IN_SCHEDULED_TODAY,
                        "Move-in scheduled today",
                        memberLabel(occupancy) + " moves in today",
                        "Complete Move-in",
                        "DashboardOccupancyList",
                        key);
            }
        }

        for (OccupancyEntity occupancy : moveOutsToday) {
            for (UUID managerId : managerIds) {
                String key = actionDedupeKey(
                        NotificationType.MOVE_OUT_SCHEDULED_TODAY, occupancy.getId(), managerId);
                expectedKeys.add(key);
                publishAction(
                        spaceId,
                        managerId,
                        occupancy,
                        NotificationType.MOVE_OUT_SCHEDULED_TODAY,
                        "Move-out scheduled today",
                        memberLabel(occupancy) + " is scheduled to exit today",
                        "Complete Vacate",
                        "DashboardOccupancyList",
                        key);
            }
        }

        for (UUID managerId : managerIds) {
            for (SpaceNotificationEntity open : notificationService.listOpenActions(spaceId, managerId)) {
                if (!OCCUPANCY_ACTION_TYPES.contains(open.getNotificationType())) {
                    continue;
                }
                if (!expectedKeys.contains(open.getDedupeKey())) {
                    notificationService.resolveOpenForEntity(
                            spaceId,
                            NotificationEntityType.OCCUPANCY,
                            open.getEntityId(),
                            open.getNotificationType());
                }
            }
        }
    }

    @Transactional
    public void onReservationCreated(OccupancyEntity occupancy) {
        UUID spaceId = occupancy.getSpace().getId();
        for (UUID managerId : managerUserIds(spaceId)) {
            notificationService.publish(PublishNotificationCommand.builder()
                    .spaceId(spaceId)
                    .userId(managerId)
                    .entityType(NotificationEntityType.OCCUPANCY)
                    .entityId(occupancy.getId())
                    .notificationType(NotificationType.RESERVATION_CREATED)
                    .category(NotificationCategory.INFORMATION)
                    .priority(NotificationPriority.MEDIUM)
                    .title("Reservation created")
                    .message(memberLabel(occupancy) + " reserved · move-in " + occupancy.getMoveInDate())
                    .actionLabel("View Occupancy")
                    .actionRoute("DashboardOccupancyList")
                    .dedupeKey("INFO:RESERVATION_CREATED:" + occupancy.getId() + ":" + managerId)
                    .build());
        }
        if (LocalDate.now().equals(occupancy.getMoveInDate())) {
            syncSpace(spaceId);
        }
    }

    @Transactional
    public void onMoveInCompleted(OccupancyEntity occupancy) {
        UUID spaceId = occupancy.getSpace().getId();
        notificationService.resolveOpenForEntity(
                spaceId,
                NotificationEntityType.OCCUPANCY,
                occupancy.getId(),
                NotificationType.RESERVATION_STARTING_TODAY);
        notificationService.resolveOpenForEntity(
                spaceId,
                NotificationEntityType.OCCUPANCY,
                occupancy.getId(),
                NotificationType.MOVE_IN_SCHEDULED_TODAY);
        for (UUID managerId : managerUserIds(spaceId)) {
            notificationService.publish(PublishNotificationCommand.builder()
                    .spaceId(spaceId)
                    .userId(managerId)
                    .entityType(NotificationEntityType.OCCUPANCY)
                    .entityId(occupancy.getId())
                    .notificationType(NotificationType.MOVE_IN_COMPLETED)
                    .category(NotificationCategory.SUCCESS)
                    .priority(NotificationPriority.LOW)
                    .title("Move-in completed")
                    .message(memberLabel(occupancy) + " moved in")
                    .actionLabel("View Occupancy")
                    .actionRoute("DashboardOccupancyList")
                    .dedupeKey("INFO:MOVE_IN_COMPLETED:" + occupancy.getId() + ":" + managerId)
                    .build());
        }
    }

    @Transactional
    public void onMoveOutCompleted(OccupancyEntity occupancy) {
        UUID spaceId = occupancy.getSpace().getId();
        notificationService.resolveOpenForEntity(
                spaceId,
                NotificationEntityType.OCCUPANCY,
                occupancy.getId(),
                NotificationType.MOVE_OUT_SCHEDULED_TODAY);
        for (UUID managerId : managerUserIds(spaceId)) {
            notificationService.publish(PublishNotificationCommand.builder()
                    .spaceId(spaceId)
                    .userId(managerId)
                    .entityType(NotificationEntityType.OCCUPANCY)
                    .entityId(occupancy.getId())
                    .notificationType(NotificationType.MOVE_OUT_COMPLETED)
                    .category(NotificationCategory.INFORMATION)
                    .priority(NotificationPriority.LOW)
                    .title("Move-out completed")
                    .message(memberLabel(occupancy) + " vacated")
                    .actionLabel("View Occupancy")
                    .actionRoute("DashboardOccupancyList")
                    .dedupeKey("INFO:MOVE_OUT_COMPLETED:" + occupancy.getId() + ":" + managerId)
                    .build());
        }
    }

    @Transactional
    public void onReservationCancelled(OccupancyEntity occupancy) {
        UUID spaceId = occupancy.getSpace().getId();
        notificationService.resolveOpenForEntity(
                spaceId,
                NotificationEntityType.OCCUPANCY,
                occupancy.getId(),
                NotificationType.RESERVATION_STARTING_TODAY);
        notificationService.resolveOpenForEntity(
                spaceId,
                NotificationEntityType.OCCUPANCY,
                occupancy.getId(),
                NotificationType.MOVE_IN_SCHEDULED_TODAY);
    }

    private void publishAction(
            UUID spaceId,
            UUID managerId,
            OccupancyEntity occupancy,
            NotificationType type,
            String title,
            String message,
            String actionLabel,
            String actionRoute,
            String dedupeKey) {
        notificationService.publish(PublishNotificationCommand.builder()
                .spaceId(spaceId)
                .userId(managerId)
                .entityType(NotificationEntityType.OCCUPANCY)
                .entityId(occupancy.getId())
                .notificationType(type)
                .category(NotificationCategory.ACTION_REQUIRED)
                .priority(NotificationPriority.HIGH)
                .title(title)
                .message(message)
                .actionLabel(actionLabel)
                .actionRoute(actionRoute)
                .dedupeKey(dedupeKey)
                .build());
    }

    private static String memberLabel(OccupancyEntity occupancy) {
        if (occupancy.getMember() != null && occupancy.getMember().getFullName() != null) {
            return occupancy.getMember().getFullName();
        }
        return "Member";
    }

    private List<UUID> managerUserIds(UUID spaceId) {
        return membershipRepository.findBySpaceIdAndStatus(spaceId, MembershipStatus.ACTIVE).stream()
                .filter(m -> m.getRole() == MembershipRole.OWNER || m.getRole() == MembershipRole.MANAGER)
                .map(m -> m.getUser().getId())
                .distinct()
                .toList();
    }

    private static String actionDedupeKey(NotificationType type, UUID occupancyId, UUID userId) {
        return type.name() + ":" + occupancyId + ":" + userId;
    }
}
