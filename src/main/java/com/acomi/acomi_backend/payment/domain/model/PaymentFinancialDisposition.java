package com.acomi.acomi_backend.payment.domain.model;

/**
 * Derived financial outcome for a payment obligation after cancellation / settlement.
 * Independent of workflow {@link SpacePaymentStatus}.
 */
public enum PaymentFinancialDisposition {
    OPEN,
    CANCELLED_UNPAID,
    COLLECTED_UNSETTLED,
    FULLY_REFUNDED,
    PARTIALLY_REFUNDED,
    FULLY_RETAINED
}
