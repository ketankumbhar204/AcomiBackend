package com.acomi.acomi_backend.admin.api.dto.request;

import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminNotificationTestRequest {

    @NotNull
    private NotificationType notificationType;

    /** Must be true. This endpoint never sends a live business notification. */
    @NotNull
    private Boolean testMode;

    /** PUSH, IN_APP, or BROWSER. Browser push is reported as unavailable. */
    @Size(max = 20)
    private String channel;

    @Size(max = 120)
    private String title;

    @Size(max = 300)
    private String body;

    /** Active registrations owned by the signed-in admin. Empty means all of them. */
    @Size(max = 20)
    private List<UUID> deviceTokenIds;

    @Size(max = 64)
    private String clientRequestId;
}
