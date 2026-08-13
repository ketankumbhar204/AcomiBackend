package com.acomi.acomi_backend.common.util;

import com.acomi.acomi_backend.common.exception.BusinessException;

public final class MobileNumberNormalizer {

    private MobileNumberNormalizer() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("Mobile number is required");
        }

        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        }
        if (digits.length() != 10) {
            throw new BusinessException("Mobile number must be a 10-digit Indian number");
        }
        return digits;
    }
}
