package com.acomi.acomi_backend.payment.domain.model;

public enum MonthlyBillingNotificationStatus {
    PENDING,
    /** Firebase accepted the send. This does not mean a device displayed it. */
    FCM_ACCEPTED,
    FAILED,
    SKIPPED
}
