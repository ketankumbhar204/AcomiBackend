package com.acomi.acomi_backend.meal.api.dto.response;

import com.acomi.acomi_backend.meal.domain.model.FoodScope;
import com.acomi.acomi_backend.meal.domain.model.FoodType;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealComboEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealComboItemEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealComboResponse {

    private UUID comboId;
    private String name;
    private String description;
    private FoodScope scope;

    @JsonProperty("isActive")
    private boolean active;

    private BigDecimal price;

    private String currencyCode;

    private FoodType foodType;

    private List<MealComboItemLineResponse> items;

    public static MealComboResponse from(MealComboEntity entity, List<MealComboItemEntity> comboItems) {
        return MealComboResponse.builder()
                .comboId(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .scope(FoodScope.SPACE)
                .active(entity.isActive())
                .price(entity.getPrice())
                .currencyCode(entity.getCurrencyCode())
                .foodType(entity.getFoodType())
                .items(comboItems.stream().map(MealComboItemLineResponse::from).toList())
                .build();
    }
}
