package com.acomi.acomi_backend.meal.api.dto.response;

import com.acomi.acomi_backend.meal.domain.model.MealPlanCode;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealPlanEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealPlanResponse {

    private UUID mealPlanId;
    private MealPlanCode code;
    private String name;
    private boolean breakfastIncluded;
    private boolean lunchIncluded;
    private boolean dinnerIncluded;

    @JsonProperty("isActive")
    private boolean active;

    private int sortOrder;

    public static MealPlanResponse from(MealPlanEntity entity) {
        return MealPlanResponse.builder()
                .mealPlanId(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .breakfastIncluded(entity.isBreakfastIncluded())
                .lunchIncluded(entity.isLunchIncluded())
                .dinnerIncluded(entity.isDinnerIncluded())
                .active(entity.isActive())
                .sortOrder(entity.getSortOrder())
                .build();
    }
}
