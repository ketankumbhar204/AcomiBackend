package com.acomi.acomi_backend.notification.application.service;

import com.acomi.acomi_backend.meal.domain.model.MealParticipationStatus;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealParticipationEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.SubscriptionActivationRequestEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand;
import com.acomi.acomi_backend.notification.domain.model.NotificationCategory;
import com.acomi.acomi_backend.notification.domain.model.NotificationEntityType;
import com.acomi.acomi_backend.notification.domain.model.NotificationPriority;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer/tenant meal lifecycle notifications. Recipients come from the linked member user.
 */
@Service
@RequiredArgsConstructor
public class MealLifecycleNotificationSyncService {

    private final NotificationService notificationService;

    @Transactional
    public void onSubscriptionApproved(SubscriptionActivationRequestEntity request, UUID actorId) {
        MemberEntity member = request.getMember();
        UUID userId = linkedUserId(member);
        if (userId == null || request.getSpace() == null) {
            return;
        }
        String planName = request.getPlan() != null && request.getPlan().getName() != null
                ? request.getPlan().getName()
                : "your plan";
        notificationService.publish(PublishNotificationCommand.builder()
                .spaceId(request.getSpace().getId())
                .userId(userId)
                .actorId(actorId)
                .entityType(NotificationEntityType.SUBSCRIPTION)
                .entityId(request.getId())
                .notificationType(NotificationType.SUBSCRIPTION_ACTIVATION_APPROVED)
                .category(NotificationCategory.SUCCESS)
                .priority(NotificationPriority.HIGH)
                .title("Subscription approved")
                .message(planName + " is now active at " + spaceName(request.getSpace()) + ".")
                .actionLabel("View Meals")
                .actionRoute("Meals")
                .dedupeKey("INFO:SUBSCRIPTION_ACTIVATION_APPROVED:" + request.getId() + ":" + userId)
                .build());
    }

    @Transactional
    public void onSubscriptionRejected(SubscriptionActivationRequestEntity request, UUID actorId) {
        MemberEntity member = request.getMember();
        UUID userId = linkedUserId(member);
        if (userId == null || request.getSpace() == null) {
            return;
        }
        notificationService.publish(PublishNotificationCommand.builder()
                .spaceId(request.getSpace().getId())
                .userId(userId)
                .actorId(actorId)
                .entityType(NotificationEntityType.SUBSCRIPTION)
                .entityId(request.getId())
                .notificationType(NotificationType.SUBSCRIPTION_ACTIVATION_REJECTED)
                .category(NotificationCategory.WARNING)
                .priority(NotificationPriority.MEDIUM)
                .title("Subscription request declined")
                .message("Your subscription request at " + spaceName(request.getSpace()) + " was not approved.")
                .actionLabel("View Meals")
                .actionRoute("Meals")
                .dedupeKey("INFO:SUBSCRIPTION_ACTIVATION_REJECTED:" + request.getId() + ":" + userId)
                .build());
    }

    @Transactional
    public void onMealBalanceUpdated(MemberEntity member, String title, String message) {
        UUID userId = linkedUserId(member);
        if (userId == null || member.getSpace() == null) {
            return;
        }
        UUID spaceId = member.getSpace().getId();
        notificationService.publish(PublishNotificationCommand.builder()
                .spaceId(spaceId)
                .userId(userId)
                .entityType(NotificationEntityType.SUBSCRIPTION)
                .entityId(member.getId())
                .notificationType(NotificationType.MEAL_BALANCE_UPDATED)
                .category(NotificationCategory.INFORMATION)
                .priority(NotificationPriority.MEDIUM)
                .title(title)
                .message(message)
                .actionLabel("View Meals")
                .actionRoute("Meals")
                .dedupeKey("INFO:MEAL_BALANCE_UPDATED:" + member.getId() + ":" + userId + ":"
                        + (System.currentTimeMillis() / 60_000))
                .build());
    }

    @Transactional
    public void onParticipationChanged(MealParticipationEntity participation, MealParticipationStatus newStatus) {
        MemberEntity member = participation.getMember();
        UUID userId = linkedUserId(member);
        if (userId == null || participation.getSpace() == null) {
            return;
        }
        String statusLabel = switch (newStatus) {
            case PAUSED -> "paused";
            case STOPPED -> "stopped";
            case ACTIVE -> "resumed";
        };
        notificationService.publish(PublishNotificationCommand.builder()
                .spaceId(participation.getSpace().getId())
                .userId(userId)
                .entityType(NotificationEntityType.DAILY_MENU)
                .entityId(participation.getId())
                .notificationType(NotificationType.MEAL_PARTICIPATION_CHANGED)
                .category(NotificationCategory.INFORMATION)
                .priority(NotificationPriority.MEDIUM)
                .title("Meal participation updated")
                .message("Your meal participation at "
                        + spaceName(participation.getSpace())
                        + " was "
                        + statusLabel
                        + ".")
                .actionLabel("View Meals")
                .actionRoute("Meals")
                .dedupeKey("INFO:MEAL_PARTICIPATION_CHANGED:"
                        + participation.getId()
                        + ":"
                        + newStatus
                        + ":"
                        + userId)
                .build());
    }

    private static UUID linkedUserId(MemberEntity member) {
        if (member == null) {
            return null;
        }
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
