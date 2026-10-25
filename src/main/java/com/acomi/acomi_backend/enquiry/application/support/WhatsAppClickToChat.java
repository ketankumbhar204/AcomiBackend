package com.acomi.acomi_backend.enquiry.application.support;

/**
 * Digits for wa.me. Matches the mobile and web photo-request helper:
 * Indian locals become {@code 91XXXXXXXXXX}. Existing country codes are kept.
 * This does not send a message.
 */
public final class WhatsAppClickToChat {

    private WhatsAppClickToChat() {}

    public static String digits(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("\\D", "");
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        if (digits.isEmpty()) {
            return null;
        }
        if (digits.length() == 10 && indianMobile(digits)) {
            return "91" + digits;
        }
        if (digits.length() == 11 && digits.startsWith("0") && indianMobile(digits.substring(1))) {
            return "91" + digits.substring(1);
        }
        if (digits.length() == 12 && digits.startsWith("91") && indianMobile(digits.substring(2))) {
            return digits;
        }
        if (digits.length() >= 11 && digits.length() <= 15 && !digits.startsWith("0")) {
            return digits;
        }
        return null;
    }

    private static boolean indianMobile(String digits) {
        return digits.length() == 10 && digits.charAt(0) >= '6' && digits.charAt(0) <= '9';
    }
}
