package com.acomi.acomi_backend.notification.api.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserNotificationListResponse {
    List<UserNotificationResponse> notifications;
    long unreadCount;
    int page;
    int size;
    long totalElements;
    int totalPages;
}
