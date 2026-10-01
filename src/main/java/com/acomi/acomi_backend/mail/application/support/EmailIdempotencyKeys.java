package com.acomi.acomi_backend.mail.application.support;

import com.acomi.acomi_backend.mail.domain.model.EmailEventType;
import java.util.UUID;

public final class EmailIdempotencyKeys {

    private EmailIdempotencyKeys() {}

    public static String enquiryShared(UUID enquiryId) {
        return key(EmailEventType.ENQUIRY_SHARED, enquiryId);
    }

    /** Per-recipient idempotency for explicit WEB email delivery. */
    public static String enquirySharedTo(UUID enquiryId, String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        return EmailEventType.ENQUIRY_SHARED.name() + ":" + enquiryId + ":" + normalized;
    }

    public static String enquirySubmitted(UUID enquiryId) {
        return key(EmailEventType.ENQUIRY_SUBMITTED, enquiryId);
    }

    public static String enquirySubmittedSupport(UUID enquiryId) {
        return key(EmailEventType.ENQUIRY_SUBMITTED_SUPPORT, enquiryId);
    }

    public static String enquiryRejected(UUID enquiryId) {
        return key(EmailEventType.ENQUIRY_REJECTED, enquiryId);
    }

    public static String inquiryCreditPending(UUID purchaseRequestId, UUID adminUserId) {
        return EmailEventType.INQUIRY_CREDIT_PAYMENT_PENDING.name()
                + ":"
                + purchaseRequestId
                + ":"
                + adminUserId;
    }

    public static String inquiryCreditDecision(EmailEventType eventType, UUID purchaseRequestId) {
        return eventType.name() + ":" + purchaseRequestId;
    }

    private static String key(EmailEventType eventType, UUID enquiryId) {
        return eventType.name() + ":" + enquiryId;
    }
}
