package com.acomi.acomi_backend.meal.domain.model;

/**
 * Relative to the meal's {@code pollDate}: when the default close wall-clock applies.
 */
public enum PollCloseDayOffset {
    /** Close on the calendar day before the meal date. */
    PREVIOUS_DAY,
    /** Close on the same calendar day as the meal. */
    SAME_DAY
}
