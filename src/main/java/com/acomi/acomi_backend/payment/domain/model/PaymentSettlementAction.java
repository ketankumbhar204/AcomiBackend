package com.acomi.acomi_backend.payment.domain.model;

/**
 * Owner settlement action against a payment obligation.
 *
 * <p>{@link #CANCEL_UNPAID} closes an uncollected obligation. Collected money must use refund
 * and/or retain — never a silent cancel of a PAID row.
 */
public enum PaymentSettlementAction {
    CANCEL_UNPAID,
    FULL_REFUND,
    PARTIAL_REFUND,
    RETAIN
}
