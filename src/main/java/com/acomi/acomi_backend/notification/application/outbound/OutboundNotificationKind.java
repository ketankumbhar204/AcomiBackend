package com.acomi.acomi_backend.notification.application.outbound;

/**
 * Future channel-independent outbound notification kinds (WhatsApp and others).
 *
 * <p>Phase 4 wires production WhatsApp delivery for payment overdue reminders only.
 * Complaint and meal events are documented here so a later phase can reuse
 * {@code MessageDeliveryProvider} without inventing a second notification stack.
 *
 * <ul>
 *   <li>{@link #PAYMENT_OVERDUE} — implemented via PaymentReminderService</li>
 *   <li>{@link #MEAL_PAYMENT_OVERDUE} — same reminder pipeline when meal monthly is overdue</li>
 *   <li>{@link #COMPLAINT_CREATED} / {@link #COMPLAINT_UPDATED} / {@link #COMPLAINT_RESOLVED} —
 *       future; keep using NotificationService for in-app until a channel router is added</li>
 * </ul>
 */
public enum OutboundNotificationKind {
    PAYMENT_OVERDUE,
    MEAL_PAYMENT_OVERDUE,
    MEAL_POLL_REMINDER,
    COMPLAINT_CREATED,
    COMPLAINT_UPDATED,
    COMPLAINT_RESOLVED,
    PAYMENT_RECEIVED,
    IMPORTANT_NOTIFICATION
}
