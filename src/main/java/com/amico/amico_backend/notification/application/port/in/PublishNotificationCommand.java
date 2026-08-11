package com.amico.amico_backend.notification.application.port.in;

import com.amico.amico_backend.notification.domain.model.NotificationCategory;
import com.amico.amico_backend.notification.domain.model.NotificationEntityType;
import com.amico.amico_backend.notification.domain.model.NotificationPriority;
import com.amico.amico_backend.notification.domain.model.NotificationType;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PublishNotificationCommand {
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
    String dedupeKey;
    @Builder.Default
    List<String> deliveryChannels = List.of("IN_APP");
}
