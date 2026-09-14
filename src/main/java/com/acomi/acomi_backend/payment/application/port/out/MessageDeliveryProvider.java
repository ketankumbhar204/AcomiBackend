package com.acomi.acomi_backend.payment.application.port.out;

import com.acomi.acomi_backend.payment.application.dto.PaymentReminderMessage;
import com.acomi.acomi_backend.payment.domain.model.ReminderChannel;
import lombok.Builder;
import lombok.Getter;

/**
 * Channel adapter boundary for outbound reminder delivery.
 * Billing/eligibility must never depend on a concrete WhatsApp vendor.
 */
public interface MessageDeliveryProvider {

    ReminderChannel channel();

    /** Whether the provider is ready to attempt real or logging delivery. */
    boolean isConfigured();

    MessageDeliveryResult deliver(PaymentReminderMessage message);

    @Getter
    @Builder
    final class MessageDeliveryResult {
        private final boolean success;
        private final String providerMessageId;
        private final String failureReason;
        private final String failureCode;
        private final boolean providerUnavailable;
        private final boolean retryable;

        public static MessageDeliveryResult sent(String providerMessageId) {
            return MessageDeliveryResult.builder()
                    .success(true)
                    .providerMessageId(providerMessageId)
                    .retryable(false)
                    .build();
        }

        public static MessageDeliveryResult failed(String code, String reason, boolean retryable) {
            return MessageDeliveryResult.builder()
                    .success(false)
                    .failureCode(code)
                    .failureReason(reason != null ? reason : code)
                    .providerUnavailable(false)
                    .retryable(retryable)
                    .build();
        }

        public static MessageDeliveryResult unavailable(String code) {
            return MessageDeliveryResult.builder()
                    .success(false)
                    .failureCode(code)
                    .failureReason(code)
                    .providerUnavailable(true)
                    .retryable(false)
                    .build();
        }
    }
}
