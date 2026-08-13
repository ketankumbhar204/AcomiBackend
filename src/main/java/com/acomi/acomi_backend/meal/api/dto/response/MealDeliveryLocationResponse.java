package com.acomi.acomi_backend.meal.api.dto.response;

import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealDeliveryLocationEntity;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealDeliveryLocationResponse {

    private UUID id;
    private String name;
    private String description;
    private String address;
    private boolean active;
    private int sortOrder;

    public static MealDeliveryLocationResponse from(MealDeliveryLocationEntity entity) {
        return MealDeliveryLocationResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .address(entity.getAddress())
                .active(entity.isActive())
                .sortOrder(entity.getSortOrder())
                .build();
    }
}
