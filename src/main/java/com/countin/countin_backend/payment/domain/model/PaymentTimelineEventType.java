package com.countin.countin_backend.payment.domain.model;

public enum PaymentTimelineEventType {
    CREATED,
    PROOF_UPLOADED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    RESUBMITTED,
    PAID,
    REFUNDED
}
