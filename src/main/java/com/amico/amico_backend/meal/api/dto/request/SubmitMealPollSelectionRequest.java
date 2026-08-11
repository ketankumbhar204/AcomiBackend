package com.amico.amico_backend.meal.api.dto.request;

import com.amico.amico_backend.meal.domain.model.MealType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmitMealPollSelectionRequest {

    @NotNull
    private MealType mealType;

    /** Single-select (PG, Hostel, etc.). Ignored when {@code options} is provided. */
    private UUID selectedOptionId;

    /** Multi-quantity selections (Mess spaces). */
    @Valid
    private List<SubmitMealPollOptionQuantityRequest> options;

    /** Where this meal should be delivered (Mess spaces). */
    private UUID deliveryLocationId;
}
