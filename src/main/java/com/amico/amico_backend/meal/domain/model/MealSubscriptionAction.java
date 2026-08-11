package com.amico.amico_backend.meal.domain.model;

public enum MealSubscriptionAction {
    CREATED,
    /** @deprecated Use {@link #MEALS_ADDED} — kept for legacy API clients. */
    @Deprecated
    UPDATED,
    /** @deprecated Use {@link #MEALS_ADDED} — kept for legacy API clients. */
    @Deprecated
    RENEWED,
    MEALS_ADDED,
    ENDED
}
