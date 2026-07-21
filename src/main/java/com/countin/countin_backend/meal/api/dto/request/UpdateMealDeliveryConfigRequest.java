package com.countin.countin_backend.meal.api.dto.request;

import com.countin.countin_backend.meal.domain.model.MealType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMealDeliveryConfigRequest {

    @NotEmpty
    @Valid
    private List<MealDeliveryConfigMealRequest> meals;

    @Getter
    @Setter
    public static class MealDeliveryConfigMealRequest {

        @NotNull
        private MealType mealType;

        @NotNull
        private List<UUID> allowedLocationIds;

        private UUID defaultLocationId;
    }
}
