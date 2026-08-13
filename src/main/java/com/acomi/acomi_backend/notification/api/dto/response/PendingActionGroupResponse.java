package com.acomi.acomi_backend.notification.api.dto.response;

import com.acomi.acomi_backend.notification.domain.model.NotificationPriority;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PendingActionGroupResponse {
    NotificationType actionType;
    String title;
    String actionLabel;
    String actionRoute;
    NotificationPriority priority;
    int count;
    List<NotificationResponse> items;
}
