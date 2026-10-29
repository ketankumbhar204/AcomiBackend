package com.acomi.acomi_backend.servicecontact.domain.model;

import com.acomi.acomi_backend.common.exception.BusinessException;
import java.util.Arrays;
import java.util.Locale;

/** Stable category codes. Labels can change without rewriting stored contacts. */
public enum ServiceContactCategory {
    GROCERIES_WHOLESALE("Groceries and wholesale"),
    MILK_DAIRY("Milk and dairy"),
    WATER_SUPPLY("Water supply"),
    FOOD_BAKERY("Food and bakery"),
    VEGETABLES_FRUITS("Vegetables and fruits"),
    OTHER_FOOD("Other food supplies"),
    ELECTRICAL("Electrical"),
    PLUMBING("Plumbing and water systems"),
    CARPENTRY("Carpentry and furniture"),
    CIVIL_PAINTING("Civil work and painting"),
    APPLIANCES_HVAC("Appliances and HVAC"),
    CLEANING("Cleaning and housekeeping"),
    PEST_WASTE("Pest and waste management"),
    CARE_MEDICAL("Care and medical support"),
    GAS_KITCHEN("Gas and kitchen equipment"),
    INTERNET("Internet and networking"),
    SECURITY("Security and surveillance"),
    FIRE_SAFETY("Fire and building safety"),
    LIFT_FACILITY("Lift and facility maintenance"),
    TRANSPORT("Transport and delivery"),
    PROFESSIONAL("Professional services"),
    OTHER("Other");

    private final String label;

    ServiceContactCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ServiceContactCategory parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("INVALID_CATEGORY", "Category is required");
        }
        String code = raw.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(value -> value.name().equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException("INVALID_CATEGORY", "Category is not supported"));
    }
}
