package com.acomi.acomi_backend.notification.application.service;

import com.acomi.acomi_backend.notification.application.event.NotificationCreatedEvent;
import com.acomi.acomi_backend.notification.config.PushProperties;
import com.acomi.acomi_backend.notification.infrastructure.firebase.FirebaseMessagingService;
import com.acomi.acomi_backend.notification.infrastructure.persistence.entity.UserDeviceTokenEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
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
                    "FCM send failure notificationId={} type={} message={}",
                    event.getNotificationId(),
                    event.getNotificationType(),
                    ex.getMessage());
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
