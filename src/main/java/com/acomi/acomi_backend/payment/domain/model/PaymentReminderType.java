package com.acomi.acomi_backend.payment.domain.model;

/**
 * Channel-independent reminder classification for overdue obligations.
 */
public enum PaymentReminderType {
    RENT_PAYMENT_OVERDUE,
    MEAL_PAYMENT_OVERDUE,
    GENERIC_PAYMENT_OVERDUE;

    public static PaymentReminderType fromPaymentType(SpacePaymentType paymentType) {
        if (paymentType == null) {
            return GENERIC_PAYMENT_OVERDUE;
        }
        return switch (paymentType) {
            case RENT -> RENT_PAYMENT_OVERDUE;
            case MEAL -> MEAL_PAYMENT_OVERDUE;
            default -> GENERIC_PAYMENT_OVERDUE;
        };
    }
}
