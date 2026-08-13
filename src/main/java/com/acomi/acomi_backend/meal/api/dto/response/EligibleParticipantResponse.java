package com.acomi.acomi_backend.meal.api.dto.response;

import com.acomi.acomi_backend.meal.domain.model.MealPlanCode;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EligibleParticipantResponse {

    private UUID memberId;
    private String memberName;
    private String mobileNumber;
    private MealPlanCode mealPlanCode;
    private String mealPlanName;
}
