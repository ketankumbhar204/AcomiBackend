package com.acomi.acomi_backend.property.domain.model;

/** Review lifecycle of a public-website property lead. */
public enum PropertyRegistrationStatus {
    PENDING,
    IN_REVIEW,
    CONTACTED,
    CONVERTED,
    REJECTED,
    /** Likely repeat submission. Kept for staff triage rather than discarded. */
    DUPLICATE
}
