package com.acomi.acomi_backend.common.util;

import com.acomi.acomi_backend.common.exception.BusinessException;

public final class MobileNumberNormalizer {

    private static final java.util.regex.Pattern INDIAN_MOBILE = java.util.regex.Pattern.compile("^[6-9]\\d{9}$");

    private MobileNumberNormalizer() {}

    /**
     * Returns the 10-digit national number. Accepts {@code +91}/{@code 91}/leading {@code 0}.
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("Mobile number is required");
        }

        String digits = digitsOnly(raw);
        if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (digits.length() > 10 && digits.startsWith("91")) {
            digits = digits.substring(digits.length() - 10);
        }
        if (digits.length() != 10 || !INDIAN_MOBILE.matcher(digits).matches()) {
            throw new BusinessException("Mobile number must be a 10-digit Indian number");
        }
        return digits;
    }

    /**
     * Formats a stored national number for display / dial links as {@code +91XXXXXXXXXX}.
     * Returns null when the value is blank or not a valid Indian mobile.
     */
    public static String formatWithCountryCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return "+91" + normalize(raw);
        } catch (BusinessException ex) {
            String digits = digitsOnly(raw);
            if (digits.isEmpty()) {
                return null;
            }
            return digits.startsWith("91") ? "+" + digits : "+91" + digits;
        }
    }

    private static String digitsOnly(String raw) {
        return raw.replaceAll("\\D", "");
    }
}
