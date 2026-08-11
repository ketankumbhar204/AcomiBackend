package com.amico.amico_backend.meal.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateMealPlanRequest {

    @NotBlank
    private String name;

    @NotNull
    private Boolean breakfastIncluded;

    @NotNull
    private Boolean lunchIncluded;

    @NotNull
    private Boolean dinnerIncluded;
}
