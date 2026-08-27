package com.acomi.acomi_backend.mess.domain.model;

/** Review lifecycle of a public-website mess lead. */
public enum MessRegistrationStatus {
    PENDING,
    IN_REVIEW,
    CONTACTED,
    CONVERTED,
    REJECTED,
    /** Likely repeat submission. Kept for staff triage rather than discarded. */
    DUPLICATE
}
