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
    private String referenceCode;
    private String name;
    private String description;
    private FoodScope scope;

    @JsonProperty("isActive")
    private boolean active;

    private BigDecimal price;

    private String currencyCode;

    private FoodType foodType;

    private List<MealComboItemLineResponse> items;

    private UUID photoFileId;

    /** False when this space has hidden the shared combo. Missing means available. */
    @JsonProperty("enabledForSpace")
    private boolean enabledForSpace;

    public static MealComboResponse from(MealComboEntity entity, List<MealComboItemEntity> comboItems) {
        return from(entity, comboItems, true);
    }

    public static MealComboResponse from(
            MealComboEntity entity, List<MealComboItemEntity> comboItems, boolean enabledForSpace) {
        return MealComboResponse.builder()
                .comboId(entity.getId())
                .referenceCode(entity.getReferenceCode())
                .name(entity.getName())
                .description(entity.getDescription())
                .scope(FoodScope.SPACE)
                .active(entity.isActive())
                .price(entity.getPrice())
                .currencyCode(entity.getCurrencyCode())
                .foodType(entity.getFoodType())
                .items(comboItems.stream().map(MealComboItemLineResponse::from).toList())
                .photoFileId(entity.getPhotoFileId())
                .enabledForSpace(enabledForSpace)
                .build();
    }
}
