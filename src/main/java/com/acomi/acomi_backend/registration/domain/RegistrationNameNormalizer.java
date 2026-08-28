package com.acomi.acomi_backend.registration.domain;

import org.springframework.util.StringUtils;

/** Deterministic name normalization for registration matching. No fuzzy matching. */
public final class RegistrationNameNormalizer {

    private RegistrationNameNormalizer() {}

    public static String normalize(String name) {
        if (!StringUtils.hasText(name)) {
            return "";
        }
        return name.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}
