package com.acomi.acomi_backend.notification.domain.model;

public enum NotificationEntityType {
    PAYMENT,
    MEAL_POLL,
    DAILY_MENU,
    OCCUPANCY,
    MEMBER,
    INVITATION,
    COMPLAINT,
    SUBSCRIPTION,
    SPACE,
    SPACE_ENQUIRY,
    /** User/admin inquiry-credit purchase — not scoped to a Space. */
    INQUIRY_CREDIT_PURCHASE
}
