package com.acomi.acomi_backend.common.reference;

import java.util.Locale;
import java.util.Optional;

/** Formats and parses {@code PREFIX} plus at least five digits, expanding past 99999. */
public final class BusinessReferenceCodes {

    public static final int MIN_DIGITS = 5;
    private static final String PATTERN = "^[A-Z]{3}[0-9]{5,}$";

    private BusinessReferenceCodes() {}

    public static String format(BusinessReferencePrefix prefix, long sequence) {
        if (prefix == null) {
            throw new IllegalArgumentException("prefix is required");
        }
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        String digits = Long.toString(sequence);
        if (digits.length() < MIN_DIGITS) {
            digits = "0".repeat(MIN_DIGITS - digits.length()) + digits;
        }
        return prefix.name() + digits;
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace(" ", "");
    }

    public static boolean isCanonical(String normalized) {
        return normalized != null && normalized.matches(PATTERN);
    }

    public static Optional<BusinessReferencePrefix> prefixOf(String normalized) {
        if (!isCanonical(normalized)) {
            return Optional.empty();
        }
        return BusinessReferencePrefix.fromCode(normalized);
    }
}
