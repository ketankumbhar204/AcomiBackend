package com.acomi.acomi_backend.mail.domain.model;

/**
 * Delivery states for {@code email_send_logs}.
 *
 * <ul>
 *   <li>{@link #PENDING} — intent persisted; SMTP has not been attempted yet.</li>
 *   <li>{@link #SENDING} — a send attempt is in progress (or crashed mid-send).</li>
 *   <li>{@link #SENT} — the mail transport accepted the message.</li>
 *   <li>{@link #FAILED} — last attempt failed; may be retried until max attempts.</li>
 *   <li>{@link #SKIPPED} — SMTP disabled ({@code acomi.mail.enabled=false}); not delivered.</li>
 * </ul>
 */
public enum EmailSendStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED,
    SKIPPED
}
