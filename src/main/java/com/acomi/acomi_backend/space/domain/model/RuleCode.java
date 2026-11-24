package com.acomi.acomi_backend.space.domain.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Stable codes for space house-rule suggestions. Default labels are templates only;
 * each space stores its own edited {@code rule_text} independently.
 */
public enum RuleCode {
    NO_SMOKING("No smoking on the premises"),
    NO_ALCOHOL("No alcohol on the premises"),
    NO_PETS("Pets not allowed on the premises"),
    VISITOR_HOURS("Visitors allowed only during permitted hours"),
    CLEANLINESS("Maintain cleanliness in rooms and common areas"),
    NO_LOUD_MUSIC("No loud music or disturbances"),
    RENT_DUE_DATE("Rent must be paid on or before the agreed due date"),
    GATE_CLOSING("Gate closes at the notified time"),
    ID_PROOF_REQUIRED("Valid government ID proof is required at move-in"),
    NO_COOKING("Cooking outside designated areas is not allowed"),
    LAUNDRY_RULES("Use laundry facilities as per the house schedule"),
    PARKING_RULES("Park only in allocated parking spaces"),
    FOOD_WASTE("Do not waste food; follow mess serving rules"),
    MEAL_TIMINGS("Follow notified meal timings"),
    SUBSCRIPTION_PAYMENT("Keep meal subscription payments up to date"),
    HYGIENE("Maintain personal and dining-area hygiene"),
    CUSTOM(null);

    private final String defaultLabel;

    RuleCode(String defaultLabel) {
        this.defaultLabel = defaultLabel;
    }

    public String getDefaultLabel() {
        return defaultLabel;
    }

    public boolean isCustom() {
        return this == CUSTOM;
    }

    public static Optional<RuleCode> fromValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(code -> code.name().equals(normalized)).findFirst();
    }
}
