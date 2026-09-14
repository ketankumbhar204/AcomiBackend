package com.acomi.acomi_backend.mail.application.support;

import com.acomi.acomi_backend.mail.domain.model.EmailEventType;
import java.util.UUID;

public final class EmailIdempotencyKeys {

    private EmailIdempotencyKeys() {}

    public static String enquiryShared(UUID enquiryId) {
        return key(EmailEventType.ENQUIRY_SHARED, enquiryId);
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

    private static String key(EmailEventType eventType, UUID enquiryId) {
        return eventType.name() + ":" + enquiryId;
    }
}
