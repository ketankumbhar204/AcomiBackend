package com.acomi.acomi_backend.payment.infrastructure.delivery;

/**
 * Low-level WhatsApp vendor HTTP boundary.
 * Authentication, URL construction, and response parsing stay here — not in PaymentReminderService.
 */
public interface WhatsAppVendorClient {

    VendorSendResult sendTemplate(WhatsAppTemplateSendRequest request);

    record VendorSendResult(
            boolean success,
            String providerMessageId,
            String failureCode,
            String failureReason,
            boolean retryable) {

        public static VendorSendResult ok(String messageId) {
            return new VendorSendResult(true, messageId, null, null, false);
        }

        public static VendorSendResult fail(String code, String reason, boolean retryable) {
            return new VendorSendResult(false, null, code, reason, retryable);
        }
    }
}
