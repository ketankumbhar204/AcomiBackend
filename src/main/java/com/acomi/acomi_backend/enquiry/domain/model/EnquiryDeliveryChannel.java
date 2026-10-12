package com.acomi.acomi_backend.enquiry.domain.model;

/**
 * Contact-details delivery channel — independent of {@code InquiryClientChannel}.
 * A WEB-created enquiry may deliver via APP, EMAIL, VIEW_HERE, or a combination.
 * VIEW_HERE is website-only instant contact and uses its own daily allowance.
 */
public enum EnquiryDeliveryChannel {
    APP,
    EMAIL,
    VIEW_HERE;

    public static EnquiryDeliveryChannel fromNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return EnquiryDeliveryChannel.valueOf(raw.trim().toUpperCase());
    }
}
