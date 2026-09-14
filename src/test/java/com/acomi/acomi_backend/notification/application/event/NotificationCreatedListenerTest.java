package com.acomi.acomi_backend.notification.application.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.acomi.acomi_backend.notification.application.service.PushNotificationService;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationCreatedListenerTest {

    @Mock
    private PushNotificationService pushNotificationService;

    private NotificationCreatedListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificationCreatedListener(pushNotificationService);
    }

    @Test
    void deliveryFailureDoesNotPropagate() {
        doThrow(new RuntimeException("Firebase unavailable"))
                .when(pushNotificationService)
                .deliver(any());

        listener.onNotificationCreated(NotificationCreatedEvent.builder()
                .notificationId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .notificationType(NotificationType.PAYMENT_APPROVED)
                .build());

        verify(pushNotificationService).deliver(any());
    }
}
