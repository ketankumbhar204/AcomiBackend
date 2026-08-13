package com.acomi.acomi_backend.meal.api.dto.response;

import com.acomi.acomi_backend.meal.domain.model.DailyMenuStatus;
import com.acomi.acomi_backend.meal.domain.model.MealType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealSharePreviewResponse {

    private String spaceName;
    private LocalDate menuDate;
    private MealType mealType;
    private UUID dailyMenuId;
    private DailyMenuStatus status;
    private int eligibleCount;
    private String messageText;
    private List<MealSharePreviewSlotResponse> slots;
}
