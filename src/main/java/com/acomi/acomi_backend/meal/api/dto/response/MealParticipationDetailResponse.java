package com.acomi.acomi_backend.meal.api.dto.response;

import com.acomi.acomi_backend.meal.domain.model.MealParticipationStatus;
import com.acomi.acomi_backend.meal.domain.model.MealPlanCode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealParticipationDetailResponse {

    private MealParticipationResponse participation;
    private List<MealParticipationHistoryEntryResponse> history;
}
