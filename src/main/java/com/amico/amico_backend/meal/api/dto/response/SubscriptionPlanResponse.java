package com.amico.amico_backend.meal.api.dto.response;

import com.amico.amico_backend.meal.infrastructure.persistence.entity.SubscriptionPlanEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SubscriptionPlanResponse {

    private UUID planId;
    private String name;
    private int mealsIncluded;
    private BigDecimal price;
    private String currencyCode;
    private int validityDays;

    @JsonProperty("carryForwardUnused")
    private boolean carryForwardUnused;

    private String description;
    private int sortOrder;

    @JsonProperty("isActive")
    private boolean active;

    public static SubscriptionPlanResponse from(SubscriptionPlanEntity entity) {
        return SubscriptionPlanResponse.builder()
                .planId(entity.getId())
                .name(entity.getName())
                .mealsIncluded(entity.getMealsIncluded())
                .price(entity.getPrice())
                .currencyCode(entity.getCurrencyCode())
                .validityDays(entity.getValidityDays())
                .carryForwardUnused(entity.isCarryForwardUnused())
                .description(entity.getDescription())
                .sortOrder(entity.getSortOrder())
                .active(entity.isActive())
                .build();
    }
}
