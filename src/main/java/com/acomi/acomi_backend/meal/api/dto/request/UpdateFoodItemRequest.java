package com.acomi.acomi_backend.meal.api.dto.request;

import com.acomi.acomi_backend.meal.domain.model.FoodType;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateFoodItemRequest {

    @NotBlank
    private String name;

    private UUID categoryId;

    private FoodType foodType;
}
