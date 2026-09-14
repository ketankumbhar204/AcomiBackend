package com.acomi.acomi_backend.payment.domain.model;

/**
 * Settlement view derived from stored payment amounts / workflow status.
 * Independent of overdue (which is dueDate + outstanding).
 */
public enum PaymentSettlementStatus {
    UNPAID,
    PARTIALLY_PAID,
    PAID
}
