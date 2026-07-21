package com.countin.countin_backend.meal.api.dto.request;

import com.countin.countin_backend.meal.domain.model.FoodType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateFoodItemRequest {

    @NotNull
    private UUID categoryId;

    @NotBlank
    private String name;

    private FoodType foodType;

    /** Mess-only: create item already marked as a Menu Library extra. */
    private Boolean isExtra;
}
