package com.countin.countin_backend.meal.api.dto.response;

import com.countin.countin_backend.meal.domain.model.FoodScope;
import com.countin.countin_backend.meal.domain.model.FoodType;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.FoodItemEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FoodItemResponse {

    private UUID itemId;
    private UUID categoryId;
    private String categoryName;
    private String name;
    private FoodScope scope;

    @JsonProperty("isCustom")
    private boolean custom;

    @JsonProperty("isActive")
    private boolean active;

    private FoodType foodType;

    private BigDecimal defaultPrice;
    private String currencyCode;

    @JsonProperty("isExtra")
    private boolean extra;

    public static FoodItemResponse from(FoodItemEntity entity) {
        return from(entity, null, null, false);
    }

    public static FoodItemResponse from(
            FoodItemEntity entity, BigDecimal defaultPrice, String currencyCode) {
        return from(entity, defaultPrice, currencyCode, false);
    }

    public static FoodItemResponse from(
            FoodItemEntity entity, BigDecimal defaultPrice, String currencyCode, boolean isExtra) {
        return FoodItemResponse.builder()
                .itemId(entity.getId())
                .categoryId(entity.getCategory().getId())
                .categoryName(entity.getCategory().getName())
                .name(entity.getName())
                .scope(entity.getScope())
                .custom(entity.isCustom())
                .active(entity.isActive())
                .foodType(entity.getFoodType())
                .defaultPrice(defaultPrice)
                .currencyCode(currencyCode)
                .extra(isExtra)
                .build();
    }
}
