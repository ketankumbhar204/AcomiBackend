package com.acomi.acomi_backend.notification.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.notification.application.event.NotificationCreatedEvent;
import com.acomi.acomi_backend.notification.config.PushProperties;
import com.acomi.acomi_backend.notification.domain.model.DevicePlatform;
import com.acomi.acomi_backend.notification.domain.model.NotificationEntityType;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.notification.infrastructure.firebase.FirebaseMessagingService;
import com.acomi.acomi_backend.notification.infrastructure.persistence.entity.UserDeviceTokenEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    @Mock
    private PushProperties pushProperties;

    @Mock
    private DeviceTokenService deviceTokenService;

    @Mock
    private FirebaseMessagingService firebaseMessagingService;

    @Mock
    private SpaceRepository spaceRepository;

    private PushNotificationService service;

    @BeforeEach
    void setUp() {
        service = new PushNotificationService(
                pushProperties, deviceTokenService, firebaseMessagingService, spaceRepository);
    }

    @Test
    void deliverSendsToActiveTokens() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        when(pushProperties.isEnabled()).thenReturn(true);
        when(firebaseMessagingService.isAvailable()).thenReturn(true);
        when(deviceTokenService.listActiveForUser(userId)).thenReturn(List.of(token(userId)));
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space("Sunrise PG")));
        when(firebaseMessagingService.send(anyString(), anyString(), any(), anyList())).thenReturn(List.of());

        service.deliver(event(userId, spaceId));

        verify(firebaseMessagingService).send(anyString(), anyString(), any(), anyList());
        verify(deviceTokenService, never()).deactivateTokens(any());
    }

    @Test
    void fcmFailureDoesNotThrow() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        when(pushProperties.isEnabled()).thenReturn(true);
        when(firebaseMessagingService.isAvailable()).thenReturn(true);
        when(deviceTokenService.listActiveForUser(userId)).thenReturn(List.of(token(userId)));
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.empty());
        when(firebaseMessagingService.send(anyString(), anyString(), any(), anyList()))
                .thenThrow(new RuntimeException("Firebase unavailable"));

        service.deliver(event(userId, spaceId));

        verify(deviceTokenService, never()).deactivateTokens(any());
    }

    @Test
    void invalidTokensAreDeactivated() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        when(pushProperties.isEnabled()).thenReturn(true);
        when(firebaseMessagingService.isAvailable()).thenReturn(true);
        when(deviceTokenService.listActiveForUser(userId)).thenReturn(List.of(token(userId)));
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.empty());
        when(firebaseMessagingService.send(anyString(), anyString(), any(), anyList()))
                .thenReturn(List.of("dead-token"));

        service.deliver(event(userId, spaceId));

        verify(deviceTokenService).deactivateTokens(List.of("dead-token"));
    }

    @Test
    void disabledPushSkipsFcm() {
        when(pushProperties.isEnabled()).thenReturn(false);

        service.deliver(event(UUID.randomUUID(), UUID.randomUUID()));

        verify(firebaseMessagingService, never()).send(anyString(), anyString(), any(), anyList());
    }

    @Test
    void noActiveTokenSkipsFcm() {
        UUID userId = UUID.randomUUID();
        when(pushProperties.isEnabled()).thenReturn(true);
        when(firebaseMessagingService.isAvailable()).thenReturn(true);
        when(deviceTokenService.listActiveForUser(userId)).thenReturn(List.of());

        service.deliver(event(userId, UUID.randomUUID()));

        verify(firebaseMessagingService, never()).send(anyString(), anyString(), any(), anyList());
    }

    private static NotificationCreatedEvent event(UUID userId, UUID spaceId) {
        return NotificationCreatedEvent.builder()
                .notificationId(UUID.randomUUID())
                .userId(userId)
                .spaceId(spaceId)
                .notificationType(NotificationType.PAYMENT_APPROVED)
                .entityType(NotificationEntityType.PAYMENT)
                .entityId(UUID.randomUUID())
                .title("Payment recorded")
                .body("Your payment was recorded.")
                .actionRoute("PaymentDetail")
                .build();
    }

    private static UserDeviceTokenEntity token(UUID userId) {
        return UserDeviceTokenEntity.builder()
                .userId(userId)
                .deviceId("device-1")
                .fcmToken("fcm-token")
                .platform(DevicePlatform.ANDROID)
                .active(true)
                .build();
    }

    private static SpaceEntity space(String name) {
        SpaceEntity space = new SpaceEntity();
        space.setName(name);
        return space;
    }
}
