package com.countin.countin_backend.dashboard.api.dto.response;

import com.countin.countin_backend.notification.domain.model.NotificationPriority;
import com.countin.countin_backend.notification.domain.model.NotificationType;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GlobalAttentionItemResponse {

    private NotificationType actionType;
    private String title;
    private String message;
    private int count;
    private NotificationPriority priority;
    private String actionLabel;
    private String actionRoute;
    private UUID sampleEntityId;
}
