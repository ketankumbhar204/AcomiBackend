package com.amico.amico_backend.meal.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMealPlanRequest {

    @NotBlank
    private String name;

    private Boolean breakfastIncluded;
    private Boolean lunchIncluded;
    private Boolean dinnerIncluded;
    private Boolean active;
}
