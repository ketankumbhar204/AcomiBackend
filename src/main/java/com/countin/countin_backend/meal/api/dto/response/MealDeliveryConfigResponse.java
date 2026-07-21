package com.countin.countin_backend.meal.api.dto.response;

import com.countin.countin_backend.meal.domain.model.MealType;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealDeliveryConfigResponse {

    private UUID memberId;
    private UUID participationId;
    private List<MealDeliveryConfigMealResponse> meals;
    private String overallStatus;

    @Getter
    @Builder
    public static class MealDeliveryConfigMealResponse {
        private MealType mealType;
        private List<UUID> allowedLocationIds;
        private List<MealDeliveryLocationResponse> allowedLocations;
        private UUID defaultLocationId;
        private String status;
    }
}
