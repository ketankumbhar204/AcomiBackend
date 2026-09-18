package com.acomi.acomi_backend.inquirycredit.domain.model;

/**
 * Client channel from which a seeker submits an enquiry.
 * Parsed from the X-ACOMI-CLIENT request header; defaults to WEB for missing/invalid values.
 */
public enum InquiryClientChannel {
    WEB,
    ANDROID;

    public static InquiryClientChannel fromHeader(String value) {
        if ("ANDROID".equalsIgnoreCase(value == null ? null : value.trim())) {
            return ANDROID;
        }
        return WEB;
    }
}
