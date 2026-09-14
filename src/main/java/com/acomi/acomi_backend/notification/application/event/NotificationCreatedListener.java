package com.acomi.acomi_backend.notification.application.event;

import com.acomi.acomi_backend.notification.application.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends FCM only after the business transaction that created the in-app
 * notification has committed. Failures are logged and never rethrown.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCreatedListener {

    private final PushNotificationService pushNotificationService;

    @Async("pushExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        try {
            pushNotificationService.deliver(event);
        } catch (Exception ex) {
            log.warn(
                    "Push delivery failed notificationId={} type={}",
                    event == null ? null : event.getNotificationId(),
                    event == null ? null : event.getNotificationType(),
                    ex);
        }
    }
}
