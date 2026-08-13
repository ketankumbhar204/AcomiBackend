package com.acomi.acomi_backend.meal.api.dto.response;

import com.acomi.acomi_backend.meal.domain.model.FoodScope;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.FoodCategoryEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FoodCategoryResponse {

    private UUID categoryId;
    private String name;
    private int sortOrder;
    private FoodScope scope;

    @JsonProperty("isActive")
    private boolean active;

    private long itemCount;

    public static FoodCategoryResponse from(FoodCategoryEntity entity, long itemCount) {
        return FoodCategoryResponse.builder()
                .categoryId(entity.getId())
                .name(entity.getName())
                .sortOrder(entity.getSortOrder())
                .scope(entity.getScope())
                .active(entity.isActive())
                .itemCount(itemCount)
                .build();
    }
}
