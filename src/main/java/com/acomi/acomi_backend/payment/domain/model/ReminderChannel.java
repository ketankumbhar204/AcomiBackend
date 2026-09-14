package com.acomi.acomi_backend.payment.domain.model;

/** Outbound reminder channel (independent of in-app NotificationStatus). */
public enum ReminderChannel {
    WHATSAPP,
    IN_APP,
    SMS,
    EMAIL,
    PUSH
}
