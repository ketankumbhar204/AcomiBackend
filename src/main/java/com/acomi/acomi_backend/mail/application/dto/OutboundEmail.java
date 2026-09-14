package com.acomi.acomi_backend.mail.application.dto;

/**
 * Payload handed to the mail transport. Bodies are never persisted.
 */
public record OutboundEmail(
        String to,
        String subject,
        String plainBody,
        String from,
        String fromName,
        String replyTo,
        String htmlBody) {

    public OutboundEmail(String to, String subject, String plainBody, String from, String fromName, String replyTo) {
        this(to, subject, plainBody, from, fromName, replyTo, null);
    }
}
