package com.acomi.acomi_backend.space.domain.model;

/**
 * Space-level meal billing mode.
 *
 * <p>Acomi standard: two business concepts only — pay when you eat, or eat from prepaid balance.
 * Hybrid behaviour (prepaid exhausted → pay per meal) is controlled by {@code prepaidFallbackToPayPerMeal},
 * not a separate billing type.
 */
public enum MealBillingType {
    PAY_PER_MEAL,
    PREPAID_BALANCE
}
