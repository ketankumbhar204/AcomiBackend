package com.acomi.acomi_backend.mail.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Application mail settings. SMTP host/user/password come from Spring Mail
 * environment configuration — never from this class or committed YAML.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "acomi.mail")
public class MailProperties {

    /**
     * When false, the logging transport records SKIPPED and does not open an
     * SMTP session. Local profile defaults this to true for E2E testing.
     */
    private boolean enabled = false;

    /** Envelope/from address. Env: {@code ACOMI_MAIL_FROM}. */
    private String from;

    /** Display name paired with {@link #from}. Env: {@code ACOMI_MAIL_FROM_NAME}. */
    private String fromName;

    /** Reply-To address. Env: {@code ACOMI_MAIL_REPLY_TO}. */
    private String replyTo;

    /**
     * Internal mailbox for operational alerts (new enquiry, etc.).
     * Env: {@code ACOMI_MAIL_SUPPORT_ADDRESS}.
     */
    private String supportAddress;

    private Retry retry = new Retry();

    @Getter
    @Setter
    public static class Retry {
        /** Max SMTP attempts per idempotency key, including the first send. */
        private int maxAttempts = 3;

        /** Minimum delay after {@code last_attempt_at} before another retry. */
        private long delayMs = 60_000;

        /** Scheduler interval for failed/pending rows. */
        private long jobDelayMs = 300_000;
    }

    public String resolvedReplyTo() {
        if (replyTo != null && !replyTo.isBlank()) {
            return replyTo.trim();
        }
        return from == null ? null : from.trim();
    }

    public String resolvedSupportAddress() {
        if (supportAddress != null && !supportAddress.isBlank()) {
            return supportAddress.trim();
        }
        return from == null ? null : from.trim();
    }
}
