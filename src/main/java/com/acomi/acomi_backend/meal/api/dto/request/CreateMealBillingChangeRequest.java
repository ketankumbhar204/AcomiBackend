package com.acomi.acomi_backend.meal.api.dto.request;

import com.acomi.acomi_backend.space.domain.model.MealBillingType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateMealBillingChangeRequest {

    @NotNull
    private MealBillingType requestedBillingType;

    private String customerNotes;
}
