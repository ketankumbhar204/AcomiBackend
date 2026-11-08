package com.acomi.acomi_backend.meal.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateFoodCategoryRequest {

    @NotBlank
    @Size(max = 100)
    private String name;
}
