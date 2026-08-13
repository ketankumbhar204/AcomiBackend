package com.acomi.acomi_backend.meal.api.dto.response;

import com.acomi.acomi_backend.meal.domain.model.MealPollStatus;
import com.acomi.acomi_backend.meal.domain.model.MealType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealHeadcountDetailResponse {

    private LocalDate date;
    private MealType mealType;
    private UUID pollId;
    private MealPollStatus pollStatus;
    private int mealsToPrepare;
    private int eligibleCount;
    private List<MealHeadcountOptionResponse> options;
    private List<MealHeadcountMemberResponse> noResponseMembers;
    private List<MealHeadcountDeliveryLocationResponse> deliveryBreakdown;
}
