package com.acomi.acomi_backend.mail.application.dto;

public record MailSendResult(boolean skipped, boolean success, String providerMessageId, String failureReason) {

    public static MailSendResult sent(String providerMessageId) {
        return new MailSendResult(false, true, providerMessageId, null);
    }

    public static MailSendResult disabled() {
        return new MailSendResult(true, false, null, null);
    }

    public static MailSendResult failed(String failureReason) {
        return new MailSendResult(false, false, null, failureReason);
    }
}
