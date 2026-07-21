package com.countin.countin_backend.notification.application.service;

import com.countin.countin_backend.complaint.application.service.ComplaintNotificationSyncService;
import com.countin.countin_backend.dashboard.api.dto.response.DashboardAttentionItemResponse;
import com.countin.countin_backend.dashboard.application.service.DashboardAttentionService;
import com.countin.countin_backend.dashboard.domain.model.DashboardAttentionKind;
import com.countin.countin_backend.meal.application.service.MealAccessService;
import com.countin.countin_backend.member.domain.model.MembershipRole;
import com.countin.countin_backend.member.domain.model.MembershipStatus;
import com.countin.countin_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.countin.countin_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.countin.countin_backend.notification.api.dto.response.NotificationResponse;
import com.countin.countin_backend.notification.api.dto.response.PendingActionGroupResponse;
import com.countin.countin_backend.notification.api.dto.response.PendingActionsSummaryResponse;
import com.countin.countin_backend.notification.application.port.in.PublishNotificationCommand;
import com.countin.countin_backend.notification.domain.model.NotificationCategory;
import com.countin.countin_backend.notification.domain.model.NotificationEntityType;
import com.countin.countin_backend.notification.domain.model.NotificationPriority;
import com.countin.countin_backend.notification.domain.model.NotificationType;
import com.countin.countin_backend.notification.infrastructure.persistence.entity.SpaceNotificationEntity;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Action Center: unresolved {@link NotificationCategory#ACTION_REQUIRED} notifications only.
 * Syncs payment / complaint / invitation / occupancy / meal actions from domain stores
 * before aggregating so counts stay aligned.
 */
@Service
@RequiredArgsConstructor
public class PendingActionService {

    private static final Set<NotificationType> OPERATIONAL_TYPES = EnumSet.of(
            NotificationType.MENU_NOT_PLANNED,
            NotificationType.MENU_DRAFT_PENDING_PUBLISH,
            NotificationType.MEAL_POLL_NOT_PUBLISHED,
            NotificationType.MEAL_RESPONSES_BELOW_THRESHOLD,
            NotificationType.SUBSCRIPTION_ACTIVATION_PENDING);

    /**
     * Owner/manager Action Center types that must never surface for tenants/customers.
     * Complaint / invitation types are handled separately for TENANT/CUSTOMER so STAFF
     * assignees can still receive complaint actions when they are not meal managers.
     */
    private static final Set<NotificationType> OWNER_OPERATOR_ACTION_TYPES = EnumSet.of(
            NotificationType.MENU_NOT_PLANNED,
            NotificationType.MENU_DRAFT_PENDING_PUBLISH,
            NotificationType.MEAL_POLL_NOT_PUBLISHED,
            NotificationType.MEAL_RESPONSES_BELOW_THRESHOLD,
            NotificationType.SUBSCRIPTION_ACTIVATION_PENDING,
            NotificationType.PAYMENT_NEEDS_REVIEW,
            NotificationType.PAYMENT_NEEDS_UPDATE,
            NotificationType.PAYMENT_OVERDUE,
            NotificationType.MOVE_IN_SCHEDULED_TODAY,
            NotificationType.MOVE_OUT_SCHEDULED_TODAY,
            NotificationType.RESERVATION_STARTING_TODAY);

    private static final Set<NotificationType> TENANT_HIDDEN_ACTION_TYPES = EnumSet.of(
            NotificationType.COMPLAINT_PENDING,
            NotificationType.COMPLAINT_OVERDUE);

    private static final String INVITEE_INVITATION_ROUTE = "AcceptInvitations";

    private final NotificationService notificationService;
    private final PaymentNotificationSyncService paymentNotificationSyncService;
    private final ComplaintNotificationSyncService complaintNotificationSyncService;
    private final InvitationNotificationSyncService invitationNotificationSyncService;
    private final OccupancyNotificationSyncService occupancyNotificationSyncService;
    private final DashboardAttentionService dashboardAttentionService;
    private final MealAccessService mealAccessService;
    private final SpaceMembershipRepository membershipRepository;

    @Transactional
    public PendingActionsSummaryResponse getPendingActions(UUID spaceId, UUID userId, String month) {
        String resolvedMonth = month != null && !month.isBlank() ? month : YearMonth.now().toString();
        syncAll(spaceId, userId, resolvedMonth);

        SpaceMembershipEntity membership = mealAccessService.requireViewMeals(spaceId, userId);
        boolean canManageMeals = mealAccessService.canManageMeals(membership);
        MembershipRole role = membership.getRole();
        boolean isTenantOrCustomer = role == MembershipRole.TENANT || role == MembershipRole.CUSTOMER;

        List<SpaceNotificationEntity> open = notificationService.listOpenActions(spaceId, userId);
        if (isTenantOrCustomer) {
            open = open.stream()
                    .filter(n -> !OWNER_OPERATOR_ACTION_TYPES.contains(n.getNotificationType()))
                    .filter(n -> !TENANT_HIDDEN_ACTION_TYPES.contains(n.getNotificationType()))
                    .filter(PendingActionService::isTenantVisibleInvitationOrOther)
                    .toList();
        } else if (!canManageMeals) {
            open = open.stream()
                    .filter(n -> !OWNER_OPERATOR_ACTION_TYPES.contains(n.getNotificationType()))
                    .toList();
        }
        Map<NotificationType, List<SpaceNotificationEntity>> grouped = new LinkedHashMap<>();
        for (SpaceNotificationEntity notification : open) {
            grouped
                    .computeIfAbsent(notification.getNotificationType(), ignored -> new ArrayList<>())
                    .add(notification);
        }

        List<PendingActionGroupResponse> groups = new ArrayList<>();
        int total = 0;
        for (Map.Entry<NotificationType, List<SpaceNotificationEntity>> entry : grouped.entrySet()) {
            List<NotificationResponse> items =
                    entry.getValue().stream().map(NotificationResponse::from).toList();
            SpaceNotificationEntity sample = entry.getValue().get(0);
            int count = items.size();
            total += count;
            groups.add(PendingActionGroupResponse.builder()
                    .actionType(entry.getKey())
                    .title(groupTitle(entry.getKey()))
                    .actionLabel(sample.getActionLabel())
                    .actionRoute(sample.getActionRoute())
                    .priority(sample.getPriority())
                    .count(count)
                    .items(items)
                    .build());
        }

        groups.sort(Comparator.comparing((PendingActionGroupResponse g) -> g.getPriority().ordinal())
                .reversed()
                .thenComparing(PendingActionGroupResponse::getTitle));

        return PendingActionsSummaryResponse.builder().totalCount(total).groups(groups).build();
    }

    /**
     * Refreshes actionable notification rows for a space without building the Action Center response.
     * Used by the Global Owner Dashboard to keep cross-space attention accurate in one HTTP call.
     */
    @Transactional
    public void syncSpaceActions(UUID spaceId, UUID userId, String month) {
        String resolvedMonth = month != null && !month.isBlank() ? month : YearMonth.now().toString();
        syncAll(spaceId, userId, resolvedMonth);
    }

    private void syncAll(UUID spaceId, UUID userId, String resolvedMonth) {
        paymentNotificationSyncService.syncSpaceMonth(spaceId, resolvedMonth);
        complaintNotificationSyncService.syncSpace(spaceId);
        invitationNotificationSyncService.syncSpace(spaceId);
        occupancyNotificationSyncService.syncSpace(spaceId);

        SpaceMembershipEntity membership = mealAccessService.requireViewMeals(spaceId, userId);
        if (mealAccessService.canManageMeals(membership)) {
            syncOperationalAttentionForManagers(spaceId);
        } else {
            clearOwnerOperatorActionsForUser(spaceId, userId);
        }
    }

    private void clearOwnerOperatorActionsForUser(UUID spaceId, UUID userId) {
        notificationService.resolveOpenTypesForUser(spaceId, userId, OWNER_OPERATOR_ACTION_TYPES);
    }

    /** Removes owner-only meal/ops actions incorrectly assigned to a tenant/customer. */
    @Transactional
    public void clearOwnerOnlyActionsForUser(UUID spaceId, UUID userId) {
        clearOwnerOperatorActionsForUser(spaceId, userId);
    }

    /**
     * Fan-out meal/subscription attention actions to every OWNER/MANAGER so Action Center
     * counts match across operators (previously only the requesting user received rows).
     */
    private void syncOperationalAttentionForManagers(UUID spaceId) {
        List<UUID> managerIds = membershipRepository
                .findBySpaceIdAndStatus(spaceId, MembershipStatus.ACTIVE)
                .stream()
                .filter(m -> m.getRole() == MembershipRole.OWNER || m.getRole() == MembershipRole.MANAGER)
                .map(m -> m.getUser().getId())
                .distinct()
                .toList();
        if (managerIds.isEmpty()) {
            return;
        }

        UUID attentionCaller = managerIds.get(0);
        List<DashboardAttentionItemResponse> attention = dashboardAttentionService.resolveAttention(
                spaceId, attentionCaller, LocalDate.now().plusDays(1), 0, null, null);

        Map<NotificationType, DashboardAttentionItemResponse> desired = new EnumMap<>(NotificationType.class);
        for (DashboardAttentionItemResponse item : attention) {
            NotificationType type = mapAttentionKind(item.getKind());
            if (type != null) {
                desired.put(type, item);
            }
        }

        for (NotificationType type : OPERATIONAL_TYPES) {
            if (!desired.containsKey(type)) {
                notificationService.resolveOpenByType(spaceId, type);
            }
        }

        for (Map.Entry<NotificationType, DashboardAttentionItemResponse> entry : desired.entrySet()) {
            NotificationType type = entry.getKey();
            DashboardAttentionItemResponse item = entry.getValue();
            for (UUID managerId : managerIds) {
                String dedupeKey = type.name() + ":SPACE:" + spaceId + ":" + managerId;
                notificationService.publish(PublishNotificationCommand.builder()
                        .spaceId(spaceId)
                        .userId(managerId)
                        .entityType(entityTypeFor(type))
                        .entityId(spaceId)
                        .notificationType(type)
                        .category(NotificationCategory.ACTION_REQUIRED)
                        .priority(NotificationPriority.HIGH)
                        .title(groupTitle(type))
                        .message(operationalMessage(type, item))
                        .actionLabel(operationalActionLabel(type))
                        .actionRoute(operationalRoute(type))
                        .dedupeKey(dedupeKey)
                        .build());
            }
        }
    }

    private static NotificationType mapAttentionKind(DashboardAttentionKind kind) {
        return switch (kind) {
            case NOT_PLANNED -> NotificationType.MENU_NOT_PLANNED;
            case PARTIAL_PLANNED -> NotificationType.MENU_NOT_PLANNED;
            case READY_TO_SHARE -> NotificationType.MENU_DRAFT_PENDING_PUBLISH;
            case POLL_OPEN -> NotificationType.MEAL_RESPONSES_BELOW_THRESHOLD;
            case SUBSCRIPTION_ACTIVATION_PENDING -> NotificationType.SUBSCRIPTION_ACTIVATION_PENDING;
            case PAYMENTS_OVERDUE -> null;
        };
    }

    private static NotificationEntityType entityTypeFor(NotificationType type) {
        return switch (type) {
            case SUBSCRIPTION_ACTIVATION_PENDING -> NotificationEntityType.SUBSCRIPTION;
            case MENU_NOT_PLANNED, MENU_DRAFT_PENDING_PUBLISH, MEAL_POLL_NOT_PUBLISHED, MEAL_RESPONSES_BELOW_THRESHOLD ->
                    NotificationEntityType.DAILY_MENU;
            default -> NotificationEntityType.SPACE;
        };
    }

    private static boolean isTenantVisibleInvitationOrOther(SpaceNotificationEntity notification) {
        if (notification.getNotificationType() != NotificationType.PENDING_INVITATION) {
            return true;
        }
        String route = notification.getActionRoute();
        return route != null && INVITEE_INVITATION_ROUTE.equalsIgnoreCase(route.trim());
    }

    private static String groupTitle(NotificationType type) {
        return switch (type) {
            case PAYMENT_NEEDS_REVIEW -> "Payment Reviews";
            case PAYMENT_NEEDS_UPDATE -> "Needs Update";
            case PAYMENT_UPDATE_REQUESTED -> "Payment requires update";
            case PAYMENT_OVERDUE -> "Pending Payments";
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

    private static String operationalMessage(NotificationType type, DashboardAttentionItemResponse item) {
        return switch (type) {
            case SUBSCRIPTION_ACTIVATION_PENDING ->
                    (item.getPendingSubscriptionRequestCount() != null
                                    ? item.getPendingSubscriptionRequestCount()
                                    : 0)
                            + " request(s) awaiting activation";
            case MEAL_RESPONSES_BELOW_THRESHOLD ->
                    (item.getRespondedCount() != null ? item.getRespondedCount() : 0)
                            + " of "
                            + (item.getEligibleCount() != null ? item.getEligibleCount() : 0)
                            + " responded";
            case MENU_NOT_PLANNED -> "Tomorrow's menu still needs planning";
            case MENU_DRAFT_PENDING_PUBLISH -> "Tomorrow's menu is ready to share";
            default -> groupTitle(type);
        };
    }

    private static String operationalActionLabel(NotificationType type) {
        return switch (type) {
            case MENU_NOT_PLANNED -> "Plan Menu";
            case MENU_DRAFT_PENDING_PUBLISH -> "Publish Menu";
            case MEAL_RESPONSES_BELOW_THRESHOLD -> "View Poll";
            case SUBSCRIPTION_ACTIVATION_PENDING -> "Review Requests";
            default -> "Open";
        };
    }

    private static String operationalRoute(NotificationType type) {
        return switch (type) {
            case MENU_NOT_PLANNED, MEAL_RESPONSES_BELOW_THRESHOLD -> "MenuPlanning";
            case MENU_DRAFT_PENDING_PUBLISH -> "MenuSharePreview";
            case SUBSCRIPTION_ACTIVATION_PENDING -> "SubscriptionActivationRequests";
            case PAYMENT_NEEDS_REVIEW, PAYMENT_NEEDS_UPDATE -> "Payments/pendingReview";
            case PAYMENT_UPDATE_REQUESTED -> "PaymentDetail";
            default -> "Dashboard";
        };
    }
}
