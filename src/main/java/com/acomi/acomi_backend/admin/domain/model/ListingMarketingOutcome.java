package com.acomi.acomi_backend.admin.domain.model;

public enum ListingMarketingOutcome {
    WHATSAPP_OPENED,
    SHARED_CONFIRMED,
    WHATSAPP_UNAVAILABLE,
    CALL_ATTEMPTED,
    CONTACTED_BY_PHONE,
    SKIPPED,
    /** Provider accepted an automatic Business Platform submission (not delivery/read). */
    WHATSAPP_API_SUBMITTED,
    /** Automatic Business Platform send failed or was rejected. */
    WHATSAPP_API_FAILED,
    /** Automatic send blocked (not configured, ineligible, consent, validation). */
    WHATSAPP_API_BLOCKED
}
