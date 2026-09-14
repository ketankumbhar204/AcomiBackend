package com.acomi.acomi_backend.mail.domain.model;

/**
 * Named outbound email events. Each event uses its own idempotency key so
 * future notifications for the same entity (e.g. a later enquiry rejection)
 * are not blocked by an earlier send.
 */
public enum EmailEventType {
    ENQUIRY_SHARED,
    ENQUIRY_SUBMITTED,
    ENQUIRY_SUBMITTED_SUPPORT,
    ENQUIRY_REJECTED
}
