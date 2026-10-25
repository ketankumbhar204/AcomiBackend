package com.acomi.acomi_backend.notification.application.service;

import com.acomi.acomi_backend.notification.application.event.NotificationCreatedEvent;
import com.acomi.acomi_backend.notification.config.PushProperties;
import com.acomi.acomi_backend.notification.infrastructure.firebase.FirebaseMessagingService;
import com.acomi.acomi_backend.notification.infrastructure.firebase.FirebaseMessagingService.TokenOutcome;
import com.acomi.acomi_backend.notification.infrastructure.persistence.entity.UserDeviceTokenEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Resolves device tokens and sends FCM. Failures never propagate to business transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private static final int MAX_BODY_LENGTH = 180;

    private final PushProperties pushProperties;
    private final DeviceTokenService deviceTokenService;
    private final FirebaseMessagingService firebaseMessagingService;
    private final SpaceRepository spaceRepository;

    public void deliver(NotificationCreatedEvent event) {
        if (event == null || event.getUserId() == null) {
            return;
        }
        if (!pushProperties.isEnabled() || !firebaseMessagingService.isAvailable()) {
            log.info(
                    "notification skipped channel=FCM reason=disabled notificationId={} type={}",
                    event.getNotificationId(),
                    event.getNotificationType());
            return;
        }
        List<UserDeviceTokenEntity> devices = deviceTokenService.listActiveForUser(event.getUserId());
        if (devices.isEmpty()) {
            log.info(
                    "notification skipped channel=FCM reason=no_active_token notificationId={} userId={}",
                    event.getNotificationId(),
                    event.getUserId());
            return;
        }
        String spaceName = resolveSpaceName(event.getSpaceId());
        String title = safeText(event.getTitle(), "ACOMI");
        String body = withSpaceContext(safeText(event.getBody(), title), spaceName);
        Map<String, String> data = payload(event, spaceName);
        List<String> tokens = devices.stream().map(UserDeviceTokenEntity::getFcmToken).toList();
        try {
            List<String> invalid = firebaseMessagingService.send(title, body, data, tokens);
            if (!invalid.isEmpty()) {
                deviceTokenService.deactivateTokens(invalid);
            }
            log.info(
                    "FCM send success notificationId={} type={} devices={} invalid={}",
                    event.getNotificationId(),
                    event.getNotificationType(),
                    tokens.size(),
                    invalid.size());
        } catch (Exception ex) {
            log.warn(
                    "FCM send failure notificationId={} type={} errorType={}",
                    event.getNotificationId(),
                    event.getNotificationType(),
                    ex.getClass().getSimpleName());
        }
    }

    /**
     * Synchronous send used by the admin test center. Reports provider acceptance,
     * not that a device displayed the notification.
     */
    public TrackedPushResult deliverTracked(
            String title, String body, Map<String, String> data, List<UserDeviceTokenEntity> devices) {
        List<UserDeviceTokenEntity> targets = devices == null ? List.of() : devices;
        if (!pushProperties.isEnabled()) {
            return TrackedPushResult.blocked(
                    "PUSH_DISABLED",
                    "Push is disabled for this environment. Set ACOMI_PUSH_ENABLED=true and restart the API.");
        }
        if (!firebaseMessagingService.isAvailable()) {
            return TrackedPushResult.blocked(
                    "FIREBASE_NOT_INITIALIZED",
                    "Firebase Admin is not initialized. Credentials are missing or invalid.");
        }
        if (targets.isEmpty()) {
            return TrackedPushResult.blocked(
                    "NO_REGISTRATION",
                    "This admin account has no active mobile registration. Open the ACOMI app, allow notifications, and sign in.");
        }
        List<String> tokens = targets.stream().map(UserDeviceTokenEntity::getFcmToken).toList();
        List<TokenOutcome> outcomes = firebaseMessagingService.sendTracked(title, body, data, tokens);
        List<String> invalid = new ArrayList<>();
        List<DevicePushResult> deviceResults = new ArrayList<>();
        int accepted = 0;
        int failed = 0;
        String firstError = null;
        for (int i = 0; i < targets.size(); i++) {
            UserDeviceTokenEntity device = targets.get(i);
            TokenOutcome outcome = i < outcomes.size()
                    ? outcomes.get(i)
                    : new TokenOutcome(false, "FCM_REJECTED", false);
            if (outcome.accepted()) {
                accepted++;
                deviceResults.add(new DevicePushResult(
                        device.getId(), device.getDeviceId(), device.getPlatform().name(), "ACCEPTED", null));
            } else {
                failed++;
                if (firstError == null) {
                    firstError = outcome.errorCode();
                }
                if (outcome.invalidToken()) {
                    invalid.add(device.getFcmToken());
                }
                deviceResults.add(new DevicePushResult(
                        device.getId(),
                        device.getDeviceId(),
                        device.getPlatform().name(),
                        outcome.invalidToken() ? "STALE" : "REJECTED",
                        outcome.errorCode()));
            }
        }
        if (!invalid.isEmpty()) {
            deviceTokenService.deactivateTokens(invalid);
        }
        String status = accepted == targets.size() ? "FCM_ACCEPTED" : accepted > 0 ? "PARTIAL_SUCCESS" : "FAILED";
        return new TrackedPushResult(
                status,
                accepted == targets.size() ? null : firstError,
                accepted == targets.size()
                        ? "FCM accepted the send. This does not confirm the phone displayed it."
                        : "FCM did not accept every registration.",
                targets.size(),
                accepted,
                failed,
                true,
                deviceResults);
    }

    public record DevicePushResult(
            UUID deviceTokenId, String deviceId, String platform, String outcome, String errorCode) {}

    public record TrackedPushResult(
            String status,
            String errorCode,
            String errorDetail,
            int targeted,
            int accepted,
            int failed,
            boolean fcmAttempted,
            List<DevicePushResult> devices) {

        public static TrackedPushResult blocked(String code, String detail) {
            return new TrackedPushResult(codeToStatus(code), code, detail, 0, 0, 0, false, List.of());
        }

        private static String codeToStatus(String code) {
            return "NO_REGISTRATION".equals(code) || "PUSH_DISABLED".equals(code)
                            || "FIREBASE_NOT_INITIALIZED".equals(code)
                    ? "BLOCKED"
                    : "FAILED";
        }
    }

    private Map<String, String> payload(NotificationCreatedEvent event, String spaceName) {
        Map<String, String> data = new LinkedHashMap<>();
        put(data, "type", event.getNotificationType() == null ? null : event.getNotificationType().name());
        put(data, "notificationId", event.getNotificationId());
        put(data, "spaceId", event.getSpaceId());
        put(data, "entityType", event.getEntityType() == null ? null : event.getEntityType().name());
        put(data, "entityId", event.getEntityId());
        put(data, "actionRoute", event.getActionRoute());
        put(data, "spaceName", spaceName);
        return data;
    }

    private String resolveSpaceName(UUID spaceId) {
        if (spaceId == null) {
            return null;
        }
        return spaceRepository.findById(spaceId).map(space -> space.getName()).orElse(null);
    }

    private static String withSpaceContext(String body, String spaceName) {
        if (spaceName == null || spaceName.isBlank()) {
            return body;
        }
        if (body.toLowerCase().contains(spaceName.toLowerCase())) {
            return body;
        }
        return truncate(body + " · " + spaceName.trim());
    }

    private static String safeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return truncate(value.trim());
    }

    private static String truncate(String value) {
        if (value.length() <= MAX_BODY_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_BODY_LENGTH - 1) + "…";
    }

    private static void put(Map<String, String> data, String key, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (!text.isEmpty()) {
            data.put(key, text);
        }
    }
}
