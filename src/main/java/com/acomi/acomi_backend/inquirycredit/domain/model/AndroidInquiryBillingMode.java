package com.acomi.acomi_backend.inquirycredit.domain.model;

/**
 * How ANDROID (mobile app) enquiries are billed.
 * <ul>
 *   <li>{@code FREE} — unlimited for seekers (hourly rate limit only); no wallet debit.</li>
 *   <li>{@code CREDITS} — daily free quota then paid wallet credits (same pattern as WEB/email).</li>
 * </ul>
 */
public enum AndroidInquiryBillingMode {
    FREE,
    CREDITS;

    public static AndroidInquiryBillingMode fromDb(String value) {
        if (value == null || value.isBlank()) {
            return FREE;
        }
        try {
            return AndroidInquiryBillingMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return FREE;
        }
    }
}
