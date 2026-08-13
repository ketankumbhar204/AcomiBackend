package com.acomi.acomi_backend.meal.api.dto.response;

import com.acomi.acomi_backend.meal.domain.model.MealType;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealSharePreviewSlotResponse {

    private MealType mealType;
    private UUID dailyMenuId;
    private List<MealSharePreviewLineResponse> lines;
    private String notes;
    private int eligibleCount;
}
