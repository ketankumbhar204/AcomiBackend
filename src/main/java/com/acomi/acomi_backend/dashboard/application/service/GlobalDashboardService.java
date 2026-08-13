package com.acomi.acomi_backend.dashboard.application.service;

import com.acomi.acomi_backend.dashboard.api.dto.response.GlobalActivityItemResponse;
import com.acomi.acomi_backend.dashboard.api.dto.response.GlobalAttentionItemResponse;
import com.acomi.acomi_backend.dashboard.api.dto.response.GlobalAttentionSpaceResponse;
import com.acomi.acomi_backend.dashboard.api.dto.response.GlobalDashboardResponse;
import com.acomi.acomi_backend.dashboard.api.dto.response.GlobalSpaceStatusResponse;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.notification.application.service.PendingActionService;
import com.acomi.acomi_backend.notification.domain.model.NotificationCategory;
import com.acomi.acomi_backend.notification.domain.model.NotificationPriority;
import com.acomi.acomi_backend.notification.domain.model.NotificationStatus;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.notification.infrastructure.persistence.entity.SpaceNotificationEntity;
import com.acomi.acomi_backend.notification.infrastructure.persistence.repository.SpaceNotificationRepository;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cross-space owner/manager overview for My Spaces.
 * Aggregates existing notification / pending-action rows — does not invent new business counts.
 */
@Service
@RequiredArgsConstructor
public class GlobalDashboardService {

    private static final int ATTENTION_SPACE_LIMIT = 3;
    private static final int ACTIVITY_LIMIT = 5;
    private static final int ACTIVITY_FETCH_LIMIT = 20;

    private static final Set<MembershipRole> OPERATOR_ROLES =
            EnumSet.of(MembershipRole.OWNER, MembershipRole.MANAGER);

    private static final Set<NotificationStatus> OPEN_STATUSES =
            EnumSet.of(NotificationStatus.UNREAD, NotificationStatus.READ);

    private static final Set<NotificationCategory> ACTIVITY_CATEGORIES = EnumSet.of(
            NotificationCategory.INFORMATION,
            NotificationCategory.SUCCESS,
            NotificationCategory.WARNING,
            NotificationCategory.ERROR);

    private final SpaceMembershipRepository membershipRepository;
    private final SpaceNotificationRepository notificationRepository;
    private final PendingActionService pendingActionService;

    @Transactional
    public GlobalDashboardResponse getGlobalDashboard(UUID userId, String month, boolean sync) {
        String resolvedMonth = month != null && !month.isBlank() ? month : YearMonth.now().toString();

        List<SpaceMembershipEntity> operatorMemberships = membershipRepository.findUserSpaces(userId).stream()
                .filter(m -> OPERATOR_ROLES.contains(m.getRole()))
                .toList();

        if (operatorMemberships.isEmpty()) {
            return GlobalDashboardResponse.builder()
                    .totalAttentionCount(0)
                    .unreadNotificationCount(0)
                    .attentionRequired(List.of())
                    .attentionHasMore(false)
                    .recentActivity(List.of())
                    .activityHasMore(false)
                    .spaceSummaries(List.of())
                    .build();
        }

        Map<UUID, SpaceMembershipEntity> membershipBySpace = new LinkedHashMap<>();
        for (SpaceMembershipEntity membership : operatorMemberships) {
            membershipBySpace.putIfAbsent(membership.getSpace().getId(), membership);
        }
        List<UUID> spaceIds = List.copyOf(membershipBySpace.keySet());

        if (sync) {
            for (UUID spaceId : spaceIds) {
                pendingActionService.syncSpaceActions(spaceId, userId, resolvedMonth);
            }
        }

        List<SpaceNotificationEntity> actionable = notificationRepository.findActionableByUserAndSpaces(
                userId, spaceIds, NotificationCategory.ACTION_REQUIRED, OPEN_STATUSES);

        Map<UUID, List<SpaceNotificationEntity>> bySpace = actionable.stream()
                .collect(Collectors.groupingBy(
                        SpaceNotificationEntity::getSpaceId, LinkedHashMap::new, Collectors.toList()));

        List<GlobalAttentionSpaceResponse> allAttention = new ArrayList<>();
        int totalAttention = 0;
        for (Map.Entry<UUID, List<SpaceNotificationEntity>> entry : bySpace.entrySet()) {
            UUID spaceId = entry.getKey();
            SpaceMembershipEntity membership = membershipBySpace.get(spaceId);
            if (membership == null) {
                continue;
            }
            SpaceEntity space = membership.getSpace();
            List<GlobalAttentionItemResponse> items = groupItems(entry.getValue());
            int spaceCount = entry.getValue().size();
            totalAttention += spaceCount;
            allAttention.add(GlobalAttentionSpaceResponse.builder()
                    .spaceId(spaceId)
                    .spaceName(space.getName())
                    .spaceType(space.getType() != null ? space.getType().name() : null)
                    .count(spaceCount)
                    .items(items)
                    .build());
        }

        allAttention.sort(Comparator.comparingInt(GlobalAttentionSpaceResponse::getCount).reversed()
                .thenComparing(GlobalAttentionSpaceResponse::getSpaceName, String.CASE_INSENSITIVE_ORDER));

        boolean attentionHasMore = allAttention.size() > ATTENTION_SPACE_LIMIT;

        List<SpaceNotificationEntity> recentRows = notificationRepository.findRecentByUserAndSpaces(
                userId,
                spaceIds,
                ACTIVITY_CATEGORIES,
                OPEN_STATUSES,
                PageRequest.of(0, ACTIVITY_FETCH_LIMIT));

        boolean activityHasMore = recentRows.size() >= ACTIVITY_FETCH_LIMIT;
        List<GlobalActivityItemResponse> recentActivity = recentRows.stream()
                .map(n -> toActivity(n, membershipBySpace))
                .toList();

        long unreadCount = notificationRepository.countByUserAndSpacesAndStatus(
                userId, spaceIds, NotificationStatus.UNREAD);

        Map<UUID, Integer> pendingBySpace = bySpace.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size()));

        List<GlobalSpaceStatusResponse> spaceSummaries = membershipBySpace.values().stream()
                .map(m -> {
                    UUID spaceId = m.getSpace().getId();
                    int pending = pendingBySpace.getOrDefault(spaceId, 0);
                    return GlobalSpaceStatusResponse.builder()
                            .spaceId(spaceId)
                            .spaceName(m.getSpace().getName())
                            .spaceType(m.getSpace().getType() != null ? m.getSpace().getType().name() : null)
                            .membershipRole(m.getRole().name())
                            .pendingActionCount(pending)
                            .needsAttention(pending > 0)
                            .build();
                })
                .toList();

        return GlobalDashboardResponse.builder()
                .totalAttentionCount(totalAttention)
                .unreadNotificationCount((int) unreadCount)
                .attentionRequired(allAttention)
                .attentionHasMore(attentionHasMore)
                .recentActivity(recentActivity)
                .activityHasMore(activityHasMore)
                .spaceSummaries(spaceSummaries)
                .build();
    }

    private GlobalActivityItemResponse toActivity(
            SpaceNotificationEntity n, Map<UUID, SpaceMembershipEntity> membershipBySpace) {
        SpaceMembershipEntity membership = membershipBySpace.get(n.getSpaceId());
        String spaceName = membership != null ? membership.getSpace().getName() : null;
        return GlobalActivityItemResponse.builder()
                .notificationId(n.getId())
                .spaceId(n.getSpaceId())
                .spaceName(spaceName)
                .notificationType(n.getNotificationType())
                .category(n.getCategory())
                .title(n.getTitle())
                .message(n.getMessage())
                .actionRoute(n.getActionRoute())
                .entityId(n.getEntityId())
                .createdAt(n.getCreatedAt())
                .build();
    }

    private List<GlobalAttentionItemResponse> groupItems(List<SpaceNotificationEntity> rows) {
        Map<NotificationType, List<SpaceNotificationEntity>> grouped = new LinkedHashMap<>();
        for (SpaceNotificationEntity row : rows) {
            grouped.computeIfAbsent(row.getNotificationType(), ignored -> new ArrayList<>()).add(row);
        }
        List<GlobalAttentionItemResponse> items = new ArrayList<>();
        for (Map.Entry<NotificationType, List<SpaceNotificationEntity>> entry : grouped.entrySet()) {
            SpaceNotificationEntity sample = entry.getValue().get(0);
            items.add(GlobalAttentionItemResponse.builder()
                    .actionType(entry.getKey())
                    .title(actionTitle(entry.getKey()))
                    .message(sample.getMessage())
                    .count(entry.getValue().size())
                    .priority(sample.getPriority() != null ? sample.getPriority() : NotificationPriority.MEDIUM)
                    .actionLabel(sample.getActionLabel())
                    .actionRoute(sample.getActionRoute())
                    .sampleEntityId(sample.getEntityId())
                    .build());
        }
        items.sort(Comparator.comparing((GlobalAttentionItemResponse i) -> i.getPriority().ordinal())
                .reversed()
                .thenComparing(GlobalAttentionItemResponse::getTitle));
        return items;
    }

    private static String actionTitle(NotificationType type) {
        return switch (type) {
            case PAYMENT_NEEDS_REVIEW -> "Payment Reviews";
            case PAYMENT_NEEDS_UPDATE -> "Needs Update";
            case PAYMENT_UPDATE_REQUESTED -> "Payment requires update";
            case PAYMENT_OVERDUE -> "Payments Due";
            case MENU_NOT_PLANNED -> "Menu Not Planned";
            case MENU_DRAFT_PENDING_PUBLISH -> "Menu Ready to Publish";
            case MEAL_POLL_NOT_PUBLISHED -> "Meal Not Published";
            case MEAL_RESPONSES_BELOW_THRESHOLD -> "Meal Responses Pending";
            case SUBSCRIPTION_ACTIVATION_PENDING -> "Subscription Activations";
            case PENDING_INVITATION -> "Pending Invitations";
            case MOVE_IN_SCHEDULED_TODAY -> "Today's Move-ins";
            case MOVE_OUT_SCHEDULED_TODAY -> "Today's Move-outs";
            case RESERVATION_STARTING_TODAY -> "Reservations Starting Today";
            case COMPLAINT_PENDING -> "Complaints Pending";
            case COMPLAINT_OVERDUE -> "Complaints Overdue";
            default -> type.name().replace('_', ' ');
        };
    }
}
