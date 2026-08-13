package com.acomi.acomi_backend.meal.api.dto.response;

import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealHeadcountDeliveryLocationResponse {

    private UUID locationId;
    private String locationName;
    private int totalPlates;
}
