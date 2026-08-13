package com.acomi.acomi_backend.space.domain.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum AmenityCode {
    WIFI("WiFi"),
    FOOD_INCLUDED("Food Included"),
    WASHING_MACHINE("Washing Machine"),
    HOT_WATER("Hot Water"),
    PARKING("Parking"),
    REFRIGERATOR("Refrigerator"),
    HOUSEKEEPING("Housekeeping"),
    CCTV("CCTV"),
    POWER_BACKUP("Power Backup"),
    RO_WATER("RO Water"),
    CUSTOM(null);

    private final String defaultLabel;

    AmenityCode(String defaultLabel) {
        this.defaultLabel = defaultLabel;
    }

    public String getDefaultLabel() {
        return defaultLabel;
    }

    public boolean isCustom() {
        return this == CUSTOM;
    }

    public static Optional<AmenityCode> fromValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(code -> code.name().equals(normalized)).findFirst();
    }
}
