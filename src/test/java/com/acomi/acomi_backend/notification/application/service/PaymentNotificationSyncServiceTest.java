package com.acomi.acomi_backend.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentNotificationSyncServiceTest {

    @Test
    void actionDedupeKeyIsStablePerPaymentAndUser() {
        UUID paymentId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        String key = PaymentNotificationSyncService.actionDedupeKey(
                NotificationType.PAYMENT_NEEDS_REVIEW, paymentId, userId);

        assertThat(key).isEqualTo("PAYMENT_NEEDS_REVIEW:" + paymentId + ":" + userId);
        assertThat(PaymentNotificationSyncService.actionDedupeKey(
                        NotificationType.PAYMENT_NEEDS_UPDATE, paymentId, userId))
                .isNotEqualTo(key);
    }
}
