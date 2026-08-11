package com.amico.amico_backend.meal.domain.policy;

import com.amico.amico_backend.meal.domain.model.FoodType;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.FoodItemEntity;
import java.util.Collection;

public final class FoodTypeResolver {

    private FoodTypeResolver() {}

    public static FoodType resolveStrictest(Collection<FoodType> types) {
        if (types == null || types.isEmpty()) {
            return FoodType.VEG;
        }
        boolean hasNonVeg = types.stream().anyMatch(type -> type == FoodType.NON_VEG);
        if (hasNonVeg) {
            return FoodType.NON_VEG;
        }
        boolean hasEgg = types.stream().anyMatch(type -> type == FoodType.EGG);
        if (hasEgg) {
            return FoodType.EGG;
        }
        return FoodType.VEG;
    }

    public static FoodType resolveStrictestFromItems(Collection<FoodItemEntity> items) {
        return resolveStrictest(items.stream().map(FoodItemEntity::getFoodType).toList());
    }
}
