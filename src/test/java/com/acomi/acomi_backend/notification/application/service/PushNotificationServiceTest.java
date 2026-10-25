package com.acomi.acomi_backend.notification.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.notification.application.event.NotificationCreatedEvent;
import com.acomi.acomi_backend.notification.config.PushProperties;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.notification.infrastructure.firebase.FirebaseMessagingService;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    @Mock
    private DeviceTokenService deviceTokenService;

    @Mock
    private FirebaseMessagingService firebaseMessagingService;

    @Mock
    private SpaceRepository spaceRepository;

    private PushNotificationService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        PushProperties properties = new PushProperties();
        properties.setEnabled(true);
        service = new PushNotificationService(
                properties, deviceTokenService, firebaseMessagingService, spaceRepository);
        userId = UUID.randomUUID();
    }

    @Test
    void missingTokensDoNotThrowOrSend() {
        when(firebaseMessagingService.isAvailable()).thenReturn(true);
        when(deviceTokenService.listActiveForUser(userId)).thenReturn(List.of());

        service.deliver(NotificationCreatedEvent.builder()
                .notificationId(UUID.randomUUID())
                .userId(userId)
                .notificationType(NotificationType.PHOTO_REQUEST_RECEIVED)
                .title("Photo request received")
                .body("Someone interested in Green PG has requested photos.")
                .build());

        verify(firebaseMessagingService, never()).send(any(), any(), any(), any());
    }

    @Test
    void providerFailureDoesNotPropagate() {
        when(firebaseMessagingService.isAvailable()).thenReturn(true);
        when(deviceTokenService.listActiveForUser(userId))
                .thenReturn(List.of(token("token-1")));
        when(spaceRepository.findById(any())).thenReturn(java.util.Optional.empty());
        when(firebaseMessagingService.send(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("fcm down"));

        service.deliver(NotificationCreatedEvent.builder()
                .notificationId(UUID.randomUUID())
                .userId(userId)
                .spaceId(UUID.randomUUID())
                .notificationType(NotificationType.PHOTO_REQUEST_RECEIVED)
                .title("Photo request received")
                .body("Someone interested in Green PG has requested photos.")
                .build());
    }

    private static com.acomi.acomi_backend.notification.infrastructure.persistence.entity.UserDeviceTokenEntity token(
            String value) {
        var entity =
                new com.acomi.acomi_backend.notification.infrastructure.persistence.entity.UserDeviceTokenEntity();
        entity.setFcmToken(value);
        return entity;
    }
}
