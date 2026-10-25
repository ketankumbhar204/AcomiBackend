package com.acomi.acomi_backend.admin.api.dto.response;

import com.acomi.acomi_backend.notification.api.dto.response.DeviceTokenResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AdminNotificationTestPageResponse {
    boolean pushEnabled;
    boolean firebaseReady;
    boolean browserPushImplemented;
    String credentialSource;
    boolean credentialReadable;
    String projectStatus;
    boolean fcmSendPossible;
    String reasonCode;
    String pushHint;
    List<TypeItem> types;
    List<DeviceTokenResponse> registrations;
    List<AttemptItem> history;

    @Value
    @Builder
    public static class TypeItem {
        String notificationType;
        String name;
        String description;
        String category;
        List<String> channels;
        String implementationStatus;
        boolean safeToTest;
        String defaultTitle;
        String defaultBody;
        String productionActionRoute;
    }

    @Value
    @Builder
    public static class AttemptItem {
        UUID attemptId;
        String notificationType;
        String channel;
        String status;
        int targetedCount;
        int acceptedCount;
        int failedCount;
        String errorCode;
        String errorDetail;
        LocalDateTime createdAt;
        boolean payloadConstructed;
        boolean notificationPersisted;
        boolean fcmAttempted;
        boolean fcmAccepted;
        boolean clientReceiptObserved;
        List<DeviceItem> devices;
    }

    @Value
    @Builder
    public static class DeviceItem {
        UUID deviceTokenId;
        String deviceId;
        String platform;
        String outcome;
        String errorCode;
    }
}
