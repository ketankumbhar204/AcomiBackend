package com.acomi.acomi_backend.enquiry.domain.model;

/**
 * Contact-details delivery channel — independent of {@code InquiryClientChannel}.
 * A WEB-created enquiry may deliver via APP, EMAIL, or both.
 */
public enum EnquiryDeliveryChannel {
    APP,
    EMAIL;

    public static EnquiryDeliveryChannel fromNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return EnquiryDeliveryChannel.valueOf(raw.trim().toUpperCase());
    }
}
