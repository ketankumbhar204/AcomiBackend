package com.amico.amico_backend.meal.api.dto.response;

import com.amico.amico_backend.meal.domain.model.MealPlanCode;
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
