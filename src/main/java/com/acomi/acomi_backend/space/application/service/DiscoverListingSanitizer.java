package com.acomi.acomi_backend.space.application.service;

import com.acomi.acomi_backend.registration.application.AdminLeadDefaults;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * Strips placeholders and private-unsafe values from customer-facing discovery fields.
 * Never copies owner contact, registration reference, or review notes.
 */
public final class DiscoverListingSanitizer {

    private static final BigDecimal MIN_LAT = BigDecimal.valueOf(-90);
    private static final BigDecimal MAX_LAT = BigDecimal.valueOf(90);
    private static final BigDecimal MIN_LNG = BigDecimal.valueOf(-180);
    private static final BigDecimal MAX_LNG = BigDecimal.valueOf(180);

    private DiscoverListingSanitizer() {}

    public static String text(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if ("—".equals(trimmed) || "-".equals(trimmed) || "–".equals(trimmed)) {
            return null;
        }
        return trimmed;
    }

    public static BigDecimal latitude(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(MIN_LAT) < 0 || value.compareTo(MAX_LAT) > 0) {
            return null;
        }
        return value;
    }

    public static BigDecimal longitude(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(MIN_LNG) < 0 || value.compareTo(MAX_LNG) > 0) {
            return null;
        }
        return value;
    }

    public static BigDecimal firstLatitude(BigDecimal primary, BigDecimal fallback) {
        BigDecimal sanitized = latitude(primary);
        return sanitized != null ? sanitized : latitude(fallback);
    }

    public static BigDecimal firstLongitude(BigDecimal primary, BigDecimal fallback) {
        BigDecimal sanitized = longitude(primary);
        return sanitized != null ? sanitized : longitude(fallback);
    }

    public static BigDecimal positivePrice(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return value;
    }

    /** True when any value looks like a usable phone or email. Never returns the raw value. */
    public static boolean hasUsableContact(String... values) {
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (isUsableContact(value)) {
                return true;
            }
        }
        return false;
    }

    static boolean isUsableContact(String value) {
        String trimmed = text(value);
        if (trimmed == null || AdminLeadDefaults.PLACEHOLDER_MOBILE.equals(trimmed)) {
            return false;
        }
        int digits = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isDigit(trimmed.charAt(i))) {
                digits += 1;
            }
        }
        if (digits >= 8) {
            return true;
        }
        return trimmed.contains("@") && trimmed.indexOf('.') > trimmed.indexOf('@');
    }

    public static String mapUrl(String value) {
        String trimmed = text(value);
        if (trimmed == null) {
            return null;
        }
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return null;
            }
            String lower = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(lower) && !"https".equals(lower)) {
                return null;
            }
            return trimmed;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
