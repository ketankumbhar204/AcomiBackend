package com.acomi.acomi_backend.meal.domain.model;

public enum MealPollPaymentEventType {
    PAY_LATER_SELECTED,
    MARK_AS_PAID_SELECTED,
    PROOF_SUBMITTED,
    APPROVED,
    REJECTED,
    REMINDER_SENT,
    PREPAID_OVERFLOW_PAY_LATER
}
