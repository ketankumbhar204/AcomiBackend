package com.acomi.acomi_backend.registration.application;

import com.acomi.acomi_backend.common.exception.BusinessException;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

/** Shared primary/alternate mobile rules for property and mess registration leads. */
public final class RegistrationMobiles {

    private static final Pattern INDIAN_MOBILE = Pattern.compile("^[6-9]\\d{9}$");

    private RegistrationMobiles() {}

    public static String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (!INDIAN_MOBILE.matcher(trimmed).matches()) {
            throw new BusinessException(
                    "Alternate mobile number must be a valid 10-digit Indian number", HttpStatus.BAD_REQUEST);
        }
        return trimmed;
    }

    public static String resolveAlternate(String primary, String alternate) {
        String normalized = normalizeOptional(alternate);
        assertDistinct(primary, normalized);
        return normalized;
    }

    public static void assertDistinct(String primary, String alternate) {
        if (!StringUtils.hasText(primary) || !StringUtils.hasText(alternate)) {
            return;
        }
        if (primary.trim().equals(alternate.trim())) {
            throw new BusinessException(
                    "Alternate mobile number must be different from the primary mobile number",
                    HttpStatus.BAD_REQUEST);
        }
    }
}
