package com.acomi.acomi_backend.dashboard.api.dto.response;

import com.acomi.acomi_backend.notification.domain.model.NotificationCategory;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GlobalActivityItemResponse {

    private UUID notificationId;
    private UUID spaceId;
    private String spaceName;
    private NotificationType notificationType;
    private NotificationCategory category;
    private String title;
    private String message;
    private String actionRoute;
    private UUID entityId;
    private LocalDateTime createdAt;
}
