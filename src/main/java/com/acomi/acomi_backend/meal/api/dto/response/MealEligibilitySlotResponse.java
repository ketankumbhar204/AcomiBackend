package com.acomi.acomi_backend.meal.api.dto.response;

import com.acomi.acomi_backend.meal.domain.model.MealType;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealEligibilitySlotResponse {

    private MealType mealType;
    private int eligibleCount;
    private int pausedCount;
    private boolean published;
    private List<MealEligibilityPlanBreakdownResponse> byPlan;
}
