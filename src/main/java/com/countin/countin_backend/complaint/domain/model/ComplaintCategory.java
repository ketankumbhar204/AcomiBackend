package com.countin.countin_backend.complaint.domain.model;

import com.countin.countin_backend.space.domain.model.SpaceType;
import java.util.EnumSet;
import java.util.Set;

public enum ComplaintCategory {
    MAINTENANCE,
    HOUSEKEEPING,
    FOOD,
    FOOD_QUALITY,
    FOOD_SERVICE,
    BILLING,
    SAFETY,
    SERVICE,
    OTHER;

    private static final Set<ComplaintCategory> TENANT_SPACE_CATEGORIES = EnumSet.of(
            MAINTENANCE, HOUSEKEEPING, FOOD, BILLING, SAFETY, OTHER);

    private static final Set<ComplaintCategory> MESS_CATEGORIES = EnumSet.of(
            FOOD_QUALITY, FOOD_SERVICE, BILLING, SERVICE, OTHER);

    public static Set<ComplaintCategory> allowedFor(SpaceType spaceType) {
        if (spaceType == SpaceType.MESS) {
            return MESS_CATEGORIES;
        }
        return TENANT_SPACE_CATEGORIES;
    }

    public boolean isFoodRelated() {
        return this == FOOD || this == FOOD_QUALITY || this == FOOD_SERVICE;
    }
}
