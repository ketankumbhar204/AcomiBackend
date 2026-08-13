package com.acomi.acomi_backend.meal.api.dto.request;

import com.acomi.acomi_backend.meal.domain.model.MealParticipationStatus;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMealParticipationRequest {

    private UUID mealPlanId;
    private MealParticipationStatus status;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    /** Default delivery drop point for daily meal selection (Mess spaces). */
    private UUID defaultDeliveryLocationId;
}
