package com.acomi.acomi_backend.mail.application.support;

/**
 * Redacts credential-like fragments before persisting or logging transport errors.
 */
public final class EmailErrorSanitizer {

    private static final int MAX_LENGTH = 500;

    private EmailErrorSanitizer() {}

    public static String sanitize(Throwable error) {
        if (error == null) {
            return "Email delivery failed";
        }
        String message = error.getClass().getSimpleName();
        if (error.getMessage() != null && !error.getMessage().isBlank()) {
            message = message + ": " + error.getMessage();
        }
        return sanitize(message);
    }

    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Email delivery failed";
        }
        String redacted = raw.replaceAll(
                "(?i)(password|passwd|pwd|secret|api[-_]?key|authorization|bearer)\\s*[=:]\\s*[^\\s,;]+",
                "$1=***");
        redacted = redacted.replace('\r', ' ').replace('\n', ' ').trim();
        if (redacted.length() > MAX_LENGTH) {
            return redacted.substring(0, MAX_LENGTH).trim();
        }
        return redacted;
    }
}
