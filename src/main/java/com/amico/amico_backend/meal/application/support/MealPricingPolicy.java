package com.amico.amico_backend.meal.application.support;

import com.amico.amico_backend.space.domain.model.SpaceType;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;

/**
 * Determines whether meals are billed and priced per selection (MESS) or included in
 * accommodation rent with polls used only for kitchen headcount (PG / Hostel / Co-living).
 */
public final class MealPricingPolicy {

    private MealPricingPolicy() {}

    public static boolean usesSeparateMealBilling(SpaceEntity space) {
        if (space == null || space.getType() == null) {
            return false;
        }
        return space.getType() == SpaceType.MESS;
    }

    public static boolean requiresMealPrices(SpaceEntity space) {
        return usesSeparateMealBilling(space);
    }
}
