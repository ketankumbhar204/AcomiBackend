package com.acomi.acomi_backend.admin.application.service;

import com.acomi.acomi_backend.admin.api.dto.request.AdminNotificationTestRequest;
import com.acomi.acomi_backend.admin.api.dto.response.AdminNotificationTestPageResponse;
import com.acomi.acomi_backend.admin.api.dto.response.AdminNotificationTestPageResponse.AttemptItem;
import com.acomi.acomi_backend.admin.api.dto.response.AdminNotificationTestPageResponse.DeviceItem;
import com.acomi.acomi_backend.admin.api.dto.response.AdminNotificationTestPageResponse.TypeItem;
import com.acomi.acomi_backend.admin.api.dto.response.AdminNotificationTestDeleteResponse;
import com.acomi.acomi_backend.admin.application.AdminSecurityAudit;
import com.acomi.acomi_backend.admin.application.support.AdminNotificationTestRateLimit;
import com.acomi.acomi_backend.admin.application.support.NotificationTestCatalog;
import com.acomi.acomi_backend.admin.application.support.NotificationTestCatalog.Entry;
import com.acomi.acomi_backend.admin.infrastructure.persistence.entity.AdminNotificationTestAttemptEntity;
import com.acomi.acomi_backend.admin.infrastructure.persistence.repository.AdminNotificationTestAttemptRepository;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.notification.api.dto.response.DeviceTokenResponse;
import com.acomi.acomi_backend.notification.api.dto.response.NotificationResponse;
import com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand;
import com.acomi.acomi_backend.notification.application.service.DeviceTokenService;
import com.acomi.acomi_backend.notification.application.service.NotificationService;
import com.acomi.acomi_backend.notification.application.service.PushNotificationService;
import com.acomi.acomi_backend.notification.application.service.PushNotificationService.TrackedPushResult;
import com.acomi.acomi_backend.notification.domain.model.NotificationCategory;
import com.acomi.acomi_backend.notification.domain.model.NotificationPriority;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.notification.infrastructure.firebase.FirebasePushStatus;
import com.acomi.acomi_backend.notification.infrastructure.persistence.entity.UserDeviceTokenEntity;
import com.acomi.acomi_backend.notification.infrastructure.persistence.repository.UserDeviceTokenRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends a fixture notification to the signed-in admin only.
 * Does not load or change payments, memberships, enquiries, or complaints.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminNotificationTestService {

    static final String CHANNEL_PUSH = "PUSH";
    static final String CHANNEL_IN_APP = "IN_APP";
    static final String CHANNEL_BROWSER = "BROWSER";

    private final NotificationService notificationService;
    private final PushNotificationService pushNotificationService;
    private final DeviceTokenService deviceTokenService;
    private final UserDeviceTokenRepository deviceTokenRepository;
    private final AdminNotificationTestAttemptRepository attemptRepository;
    private final FirebasePushStatus firebasePushStatus;
    private final AdminNotificationTestRateLimit rateLimit;
    private final AdminSecurityAudit securityAudit;

    @Transactional(readOnly = true)
    public AdminNotificationTestPageResponse page(UUID adminId) {
        List<UserDeviceTokenEntity> devices = deviceTokenService.listActiveForUser(adminId);
        return AdminNotificationTestPageResponse.builder()
                .pushEnabled(firebasePushStatus.pushEnabled())
                .firebaseReady(firebasePushStatus.firebaseReady())
                .browserPushImplemented(false)
                .credentialSource(firebasePushStatus.credentialSource())
                .credentialReadable(firebasePushStatus.credentialReadable())
                .projectStatus(firebasePushStatus.projectStatus())
                .fcmSendPossible(firebasePushStatus.fcmSendPossible(devices.size()))
                .reasonCode(firebasePushStatus.reasonCode())
                .pushHint(firebasePushStatus.hint(devices.size()))
                .types(NotificationTestCatalog.all().stream().map(this::toType).toList())
                .registrations(devices.stream().map(DeviceTokenResponse::from).toList())
                .history(attemptRepository.findTop30ByAdminUserIdOrderByCreatedAtDesc(adminId).stream()
                        .map(row -> toAttempt(row, List.of()))
                        .toList())
                .build();
    }

    public AttemptItem send(UUID adminId, AdminNotificationTestRequest request) {
        if (!Boolean.TRUE.equals(request.getTestMode())) {
            throw new BusinessException(
                    "TEST_MODE_REQUIRED",
                    "Notification tests must set testMode to true.",
                    HttpStatus.BAD_REQUEST);
        }
        String channel = normalizeChannel(request.getChannel());
        if (request.getClientRequestId() != null && !request.getClientRequestId().isBlank()) {
            var existing = attemptRepository.findByAdminUserIdAndClientRequestId(
                    adminId, request.getClientRequestId().trim());
            if (existing.isPresent()) {
                return toAttempt(existing.get(), List.of());
            }
        }

        rateLimit.acquire(adminId);
        try {
            return sendAcquired(adminId, request, channel);
        } finally {
            rateLimit.release(adminId);
        }
    }

    private AttemptItem sendAcquired(UUID adminId, AdminNotificationTestRequest request, String channel) {
        Entry entry = NotificationTestCatalog.entry(request.getNotificationType());
        String title = clean(request.getTitle(), "ACOMI Test: " + entry.name(), 120);
        String body = clean(
                request.getBody(),
                "Test " + entry.name() + ". No records were changed. Open ACOMI to confirm this arrived.",
                180);

        if (CHANNEL_BROWSER.equals(channel)) {
            return store(
                    adminId,
                    request,
                    channel,
                    "BLOCKED",
                    0,
                    0,
                    0,
                    "BROWSER_PUSH_NOT_IMPLEMENTED",
                    "Browser push is not implemented. This did not send a notification.",
                    false,
                    false,
                    false,
                    List.of());
        }

        List<UserDeviceTokenEntity> selected = selectDevices(adminId, request.getDeviceTokenIds());
        if (selected == null) {
            return store(
                    adminId,
                    request,
                    channel,
                    "BLOCKED",
                    0,
                    0,
                    0,
                    "REGISTRATION_OTHER_USER",
                    "A selected registration does not belong to this admin account. Nothing was sent.",
                    false,
                    false,
                    false,
                    List.of());
        }

        NotificationResponse saved = notificationService.publish(PublishNotificationCommand.builder()
                .userId(adminId)
                .actorId(adminId)
                .entityType(entry.entityType())
                .notificationType(entry.type())
                .category(NotificationCategory.INFORMATION)
                .priority(NotificationPriority.LOW)
                .title(title)
                .message(body)
                .actionLabel("View test")
                .actionRoute(NotificationTestCatalog.TEST_ACTION_ROUTE)
                .dedupeKey("TEST:" + entry.type().name() + ":" + adminId + ":" + UUID.randomUUID())
                .deliveryChannels(List.of("IN_APP"))
                .suppressAsyncPush(true)
                .build());

        Map<String, String> data = payload(entry, saved, title, body);
        if (CHANNEL_IN_APP.equals(channel)) {
            return store(
                    adminId,
                    request,
                    channel,
                    "IN_APP_SAVED",
                    0,
                    0,
                    0,
                    null,
                    "In-app notification saved. FCM was not called.",
                    true,
                    true,
                    false,
                    List.of());
        }

        TrackedPushResult push = pushNotificationService.deliverTracked(title, body, data, selected);
        List<DeviceItem> devices = push.devices().stream()
                .map(device -> DeviceItem.builder()
                        .deviceTokenId(device.deviceTokenId())
                        .deviceId(device.deviceId())
                        .platform(device.platform())
                        .outcome(device.outcome())
                        .errorCode(device.errorCode())
                        .build())
                .toList();
        log.info(
                "admin notification test type={} status={} targeted={} accepted={} failed={} fcmAttempted={}",
                entry.type(),
                push.status(),
                push.targeted(),
                push.accepted(),
                push.failed(),
                push.fcmAttempted());
        return store(
                adminId,
                request,
                channel,
                push.status(),
                push.targeted(),
                push.accepted(),
                push.failed(),
                push.errorCode(),
                push.errorDetail(),
                true,
                true,
                push.fcmAttempted(),
                devices);
    }

    /**
     * @return the admin's devices, or null when a selected id belongs to someone else
     */
    private List<UserDeviceTokenEntity> selectDevices(UUID adminId, List<UUID> requestedIds) {
        List<UserDeviceTokenEntity> active = deviceTokenService.listActiveForUser(adminId);
        if (requestedIds == null || requestedIds.isEmpty()) {
            return active;
        }
        List<UserDeviceTokenEntity> chosen = new ArrayList<>();
        for (UUID id : requestedIds) {
            if (id == null) {
                continue;
            }
            UserDeviceTokenEntity row = deviceTokenRepository.findById(id).orElse(null);
            if (row == null || !adminId.equals(row.getUserId())) {
                return null;
            }
            if (row.isActive()) {
                chosen.add(row);
            }
        }
        return chosen;
    }

    private Map<String, String> payload(Entry entry, NotificationResponse saved, String title, String body) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", entry.type().name());
        data.put("test", "true");
        data.put("actionRoute", NotificationTestCatalog.TEST_ACTION_ROUTE);
        data.put("productionActionRoute", entry.productionActionRoute());
        data.put("entityType", entry.entityType().name());
        if (saved.getNotificationId() != null) {
            data.put("notificationId", saved.getNotificationId().toString());
        }
        data.put("title", title);
        data.put("body", body);
        return data;
    }

    private AttemptItem store(
            UUID adminId,
            AdminNotificationTestRequest request,
            String channel,
            String status,
            int targeted,
            int accepted,
            int failed,
            String errorCode,
            String errorDetail,
            boolean payloadConstructed,
            boolean persisted,
            boolean fcmAttempted,
            List<DeviceItem> devices) {
        AdminNotificationTestAttemptEntity entity = AdminNotificationTestAttemptEntity.builder()
                .adminUserId(adminId)
                .notificationType(request.getNotificationType().name())
                .channel(channel)
                .status(status)
                .targetedCount(targeted)
                .acceptedCount(accepted)
                .failedCount(failed)
                .errorCode(truncate(errorCode, 64))
                .errorDetail(truncate(errorDetail, 200))
                .clientRequestId(blankToNull(request.getClientRequestId()))
                .build();
        AdminNotificationTestAttemptEntity saved = attemptRepository.save(entity);
        return toAttempt(saved, devices, payloadConstructed, persisted, fcmAttempted, "FCM_ACCEPTED".equals(status)
                || "PARTIAL_SUCCESS".equals(status));
    }

    private AttemptItem toAttempt(AdminNotificationTestAttemptEntity row, List<DeviceItem> devices) {
        boolean accepted = "FCM_ACCEPTED".equals(row.getStatus()) || "PARTIAL_SUCCESS".equals(row.getStatus());
        boolean attempted = "FCM_ACCEPTED".equals(row.getStatus())
                || "PARTIAL_SUCCESS".equals(row.getStatus())
                || "FAILED".equals(row.getStatus());
        boolean persisted = !"BROWSER_PUSH_NOT_IMPLEMENTED".equals(row.getErrorCode())
                && !"REGISTRATION_OTHER_USER".equals(row.getErrorCode());
        return toAttempt(row, devices, persisted, persisted, attempted, accepted && row.getAcceptedCount() > 0);
    }

    @Transactional
    public AdminNotificationTestDeleteResponse deleteOne(UUID adminId, UUID attemptId) {
        int deleted = attemptRepository.deleteOwned(attemptId, adminId);
        securityAudit.record(adminId, adminId, "ADMIN_NOTIFICATION_TEST_DELETE", "deleted=" + deleted);
        return AdminNotificationTestDeleteResponse.builder().deletedCount(deleted).build();
    }

    @Transactional
    public AdminNotificationTestDeleteResponse deleteAll(UUID adminId) {
        int deleted = attemptRepository.deleteOwnedByAdmin(adminId);
        securityAudit.record(adminId, adminId, "ADMIN_NOTIFICATION_TEST_CLEAR", "deleted=" + deleted);
        return AdminNotificationTestDeleteResponse.builder().deletedCount(deleted).build();
    }

    private AttemptItem toAttempt(
            AdminNotificationTestAttemptEntity row,
            List<DeviceItem> devices,
            boolean payloadConstructed,
            boolean persisted,
            boolean fcmAttempted,
            boolean fcmAccepted) {
        return AttemptItem.builder()
                .attemptId(row.getId())
                .notificationType(row.getNotificationType())
                .channel(row.getChannel())
                .status(row.getStatus())
                .targetedCount(row.getTargetedCount())
                .acceptedCount(row.getAcceptedCount())
                .failedCount(row.getFailedCount())
                .errorCode(row.getErrorCode())
                .errorDetail(row.getErrorDetail())
                .createdAt(row.getCreatedAt())
                .payloadConstructed(payloadConstructed)
                .notificationPersisted(persisted)
                .fcmAttempted(fcmAttempted)
                .fcmAccepted(fcmAccepted)
                .clientReceiptObserved(false)
                .devices(devices)
                .build();
    }

    private TypeItem toType(Entry entry) {
        return TypeItem.builder()
                .notificationType(entry.type().name())
                .name(entry.name())
                .description(entry.description())
                .category(entry.category())
                .channels(List.of("FCM mobile push", "In-app"))
                .implementationStatus(entry.implementationStatus())
                .safeToTest(entry.safeToTest())
                .defaultTitle("ACOMI Test: " + entry.name())
                .defaultBody("Test " + entry.name() + ". No records were changed.")
                .productionActionRoute(entry.productionActionRoute())
                .build();
    }

    private static String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return CHANNEL_PUSH;
        }
        String value = channel.trim().toUpperCase(Locale.ROOT);
        if (CHANNEL_PUSH.equals(value) || CHANNEL_IN_APP.equals(value) || CHANNEL_BROWSER.equals(value)) {
            return value;
        }
        throw new BusinessException("UNKNOWN_CHANNEL", "Unsupported notification channel.", HttpStatus.BAD_REQUEST);
    }

    private static String clean(String value, String fallback, int max) {
        String text = value == null || value.isBlank() ? fallback : value.trim();
        return truncate(text, max);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
