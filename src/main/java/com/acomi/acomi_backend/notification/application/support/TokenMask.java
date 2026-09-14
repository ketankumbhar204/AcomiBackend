package com.acomi.acomi_backend.notification.application.support;

/**
 * Safe log representation of an FCM device token. Never log the full value.
 */
public final class TokenMask {

    private TokenMask() {}

    public static String mask(String token) {
        if (token == null || token.isBlank()) {
            return "(empty)";
        }
        String value = token.trim();
        if (value.length() <= 10) {
            return value.charAt(0) + "…";
        }
        return value.substring(0, 6) + "…" + value.substring(value.length() - 4);
    }
}
