package com.amico.amico_backend.notification.api.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class NotificationListResponse {
    List<NotificationResponse> notifications;
    long unreadCount;
}
