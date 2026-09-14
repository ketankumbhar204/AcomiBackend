package com.acomi.acomi_backend.notification.application.event;

import com.acomi.acomi_backend.notification.domain.model.NotificationEntityType;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

/**
 * Raised after a new in-app notification row is persisted. Listeners must run
 * after the business transaction commits so FCM is never sent for a rollback.
 */
@Value
@Builder
public class NotificationCreatedEvent {
    UUID notificationId;
    UUID userId;
    UUID spaceId;
    NotificationType notificationType;
    NotificationEntityType entityType;
    UUID entityId;
    String title;
    String body;
    String actionRoute;
}
