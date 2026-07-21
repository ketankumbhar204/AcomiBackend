package com.countin.countin_backend.meal.api.dto.response;

import com.countin.countin_backend.meal.domain.model.FoodType;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealComboItemEntity;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealComboItemLineResponse {

    private UUID itemId;
    private String name;
    private FoodType foodType;
    /** Included units inside the combo; defaults to 1 when absent on older clients/data. */
    private int quantity;

    public static MealComboItemLineResponse from(MealComboItemEntity entity) {
        int quantity = entity.getQuantity() > 0 ? entity.getQuantity() : 1;
        return MealComboItemLineResponse.builder()
                .itemId(entity.getItem().getId())
                .name(entity.getItem().getName())
                .foodType(entity.getItem().getFoodType())
                .quantity(quantity)
                .build();
    }
}
