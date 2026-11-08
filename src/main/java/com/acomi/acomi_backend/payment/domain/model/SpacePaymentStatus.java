package com.acomi.acomi_backend.payment.domain.model;

public enum SpacePaymentStatus {
    PENDING,
    PROOF_UPLOADED,
    UNDER_REVIEW,
    PAID,
    REJECTED,
    UPDATE_REQUESTED,
    /** Uncollected obligation cancelled (reservation cancel or owner cancel). */
    CANCELLED,
    /** Collected amount fully returned via completed refund transactions. */
    REFUNDED
}
