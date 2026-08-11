package com.amico.amico_backend.notification.api.dto.response;

import com.amico.amico_backend.notification.domain.model.NotificationCategory;
import com.amico.amico_backend.notification.domain.model.NotificationEntityType;
import com.amico.amico_backend.notification.domain.model.NotificationPriority;
import com.amico.amico_backend.notification.domain.model.NotificationStatus;
import com.amico.amico_backend.notification.domain.model.NotificationType;
import com.amico.amico_backend.notification.infrastructure.persistence.entity.SpaceNotificationEntity;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class NotificationResponse {
    UUID notificationId;
    UUID spaceId;
    UUID organizationId;
    UUID userId;
    UUID actorId;
    NotificationEntityType entityType;
    UUID entityId;
    NotificationType notificationType;
    NotificationCategory category;
    NotificationPriority priority;
    String title;
    String message;
    String actionLabel;
    String actionRoute;
    NotificationStatus status;
    LocalDateTime readAt;
    LocalDateTime resolvedAt;
    List<String> deliveryChannels;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    public static NotificationResponse from(SpaceNotificationEntity entity) {
        return NotificationResponse.builder()
                .notificationId(entity.getId())
                .spaceId(entity.getSpaceId())
                .organizationId(entity.getOrganizationId())
                .userId(entity.getUserId())
                .actorId(entity.getActorId())
                .entityType(entity.getEntityType())
                .entityId(entity.getEntityId())
                .notificationType(entity.getNotificationType())
                .category(entity.getCategory())
                .priority(entity.getPriority())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .actionLabel(entity.getActionLabel())
                .actionRoute(entity.getActionRoute())
                .status(entity.getStatus())
                .readAt(entity.getReadAt())
                .resolvedAt(entity.getResolvedAt())
                .deliveryChannels(parseChannels(entity.getDeliveryChannels()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private static List<String> parseChannels(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of("IN_APP");
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
