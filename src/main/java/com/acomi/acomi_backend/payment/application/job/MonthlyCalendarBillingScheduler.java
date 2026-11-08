package com.acomi.acomi_backend.payment.application.job;

import com.acomi.acomi_backend.payment.application.service.MonthlyBillingNotificationService;
import com.acomi.acomi_backend.payment.application.service.MonthlyCalendarBillingService;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Calendar-month rent generation. The 1st run and the daily catch-up call the
 * same idempotent service. Notification delivery is a separate schedule so a
 * Firebase failure never creates another payment.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MonthlyCalendarBillingScheduler {

    private final MonthlyCalendarBillingService billingService;
    private final MonthlyBillingNotificationService notificationService;

    @Value("${acomi.billing.monthly-rent.catch-up-on-startup:false}")
    private boolean catchUpOnStartup;

    /** 01:10 on the 1st, Asia/Kolkata, after the open-ended month-close. */
    @Scheduled(
            cron = "${acomi.billing.monthly-rent.cron:0 10 1 1 * *}",
            zone = "${acomi.billing.monthly-rent.zone:Asia/Kolkata}")
    public void onFirstOfMonth() {
        billingService.runDueCycles();
    }

    /** Recovers a missed 1st without waiting for the next month. */
    @Scheduled(
            cron = "${acomi.billing.monthly-rent.catch-up-cron:0 20 2 * * *}",
            zone = "${acomi.billing.monthly-rent.zone:Asia/Kolkata}")
    public void catchUp() {
        billingService.runDueCycles();
    }

    @Scheduled(
            cron = "${acomi.billing.monthly-rent.notification-cron:0 */15 * * * *}",
            zone = "${acomi.billing.monthly-rent.zone:Asia/Kolkata}")
    public void deliverNotifications() {
        notificationService.reconcileMissingOutbox();
        for (UUID id : notificationService.dueIds(LocalDateTime.now())) {
            try {
                notificationService.deliverOne(id);
            } catch (Exception ex) {
                log.warn("Monthly rent notification failed outbox={}", id, ex);
            }
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void catchUpAfterStartup() {
        if (!catchUpOnStartup) {
            return;
        }
        billingService.runDueCycles();
    }
}
