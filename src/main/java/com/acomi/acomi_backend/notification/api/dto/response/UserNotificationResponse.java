package com.acomi.acomi_backend.notification.api.dto.response;

import com.acomi.acomi_backend.notification.domain.model.NotificationStatus;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.notification.infrastructure.persistence.entity.SpaceNotificationEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

/**
 * Requester-facing notification. Intentionally omits actorId, userId, and any owner contact.
 */
@Value
@Builder
public class UserNotificationResponse {
    UUID notificationId;
    UUID spaceId;
    UUID enquiryId;
    NotificationType notificationType;
    String title;
    String message;
    String actionLabel;
    String actionRoute;
    @JsonProperty("read")
    boolean read;
    LocalDateTime createdAt;

    public static UserNotificationResponse from(SpaceNotificationEntity entity) {
        return UserNotificationResponse.builder()
                .notificationId(entity.getId())
                .spaceId(entity.getSpaceId())
                .enquiryId(entity.getEntityId())
                .notificationType(entity.getNotificationType())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .actionLabel(entity.getActionLabel())
                .actionRoute(entity.getActionRoute())
                .read(entity.getStatus() != NotificationStatus.UNREAD)
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
