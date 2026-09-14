package com.acomi.acomi_backend.registration.application;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.util.MobileNumberNormalizer;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

/** Shared primary/alternate/additional mobile rules for property and mess registration leads. */
public final class RegistrationMobiles {

    private RegistrationMobiles() {}

    public static String normalizeOptional(String value) {
        return normalizeOptional(value, "Alternate mobile number");
    }

    public static String normalizeOptional(String value, String label) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return MobileNumberNormalizer.normalize(value);
        } catch (BusinessException ex) {
            throw new BusinessException(
                    label + " must be a valid 10-digit Indian number", HttpStatus.BAD_REQUEST);
        }
    }

    public static String resolveAlternate(String primary, String alternate) {
        String normalized = normalizeOptional(alternate);
        assertDistinct(primary, normalized, "Alternate mobile number must be different from the primary mobile number");
        return normalized;
    }

    public static String resolveAdditional(String primary, String alternate, String additional) {
        String normalized = normalizeOptional(additional, "Additional mobile number");
        assertDistinct(
                primary,
                normalized,
                "Additional mobile number must be different from the primary mobile number");
        assertDistinct(
                alternate,
                normalized,
                "Additional mobile number must be different from the alternate mobile number");
        return normalized;
    }

    public static void assertDistinct(String primary, String alternate) {
        assertDistinct(
                primary,
                alternate,
                "Alternate mobile number must be different from the primary mobile number");
    }

    public static void assertDistinct(String left, String right, String message) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return;
        }
        if (left.trim().equals(right.trim())) {
            throw new BusinessException(message, HttpStatus.BAD_REQUEST);
        }
    }
}
