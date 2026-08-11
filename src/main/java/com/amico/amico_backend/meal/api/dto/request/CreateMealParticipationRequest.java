package com.amico.amico_backend.meal.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateMealParticipationRequest {

    @NotNull
    private UUID memberId;

    @NotNull
    private UUID mealPlanId;

    @NotNull
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private UUID defaultDeliveryLocationId;
}
