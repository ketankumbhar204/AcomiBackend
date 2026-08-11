package com.amico.amico_backend.meal.api.dto.response;

import com.amico.amico_backend.meal.domain.model.DailyMenuEntryType;
import com.amico.amico_backend.meal.domain.model.FoodType;
import com.amico.amico_backend.meal.domain.model.MealPollOptionType;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.DailyMenuEntryEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealPollOptionEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealPollOptionResponse {

    private UUID id;
    private MealPollOptionType optionType;
    private int sortOrder;
    private String label;
    private String detail;
    private UUID dailyMenuEntryId;
    private BigDecimal price;
    private String currencyCode;
    private FoodType foodType;

    @JsonProperty("isExtra")
    private boolean extra;

    public static MealPollOptionResponse from(MealPollOptionEntity option) {
        DailyMenuEntryEntity entry = option.getDailyMenuEntry();
        return MealPollOptionResponse.builder()
                .id(option.getId())
                .optionType(option.getOptionType())
                .sortOrder(option.getSortOrder())
                .label(option.getLabel())
                .detail(option.getDetail())
                .dailyMenuEntryId(entry != null ? entry.getId() : null)
                .price(resolvePrice(entry))
                .currencyCode(resolveCurrencyCode(entry))
                .foodType(resolveFoodType(entry))
                .extra(entry != null && entry.isExtra())
                .build();
    }

    private static FoodType resolveFoodType(DailyMenuEntryEntity entity) {
        if (entity == null) {
            return null;
        }
        if (entity.getEntryType() == DailyMenuEntryType.COMBO && entity.getCombo() != null) {
            return entity.getCombo().getFoodType();
        }
        if (entity.getEntryType() == DailyMenuEntryType.ITEM && entity.getItem() != null) {
            return entity.getItem().getFoodType();
        }
        return null;
    }

    private static BigDecimal resolvePrice(DailyMenuEntryEntity entity) {
        if (entity == null) {
            return null;
        }
        if (entity.getEntryType() == DailyMenuEntryType.COMBO
                && entity.getCombo() != null
                && entity.getCombo().getPrice() != null) {
            return entity.getCombo().getPrice();
        }
        return entity.getPrice();
    }

    private static String resolveCurrencyCode(DailyMenuEntryEntity entity) {
        if (entity == null) {
            return null;
        }
        if (entity.getEntryType() == DailyMenuEntryType.COMBO
                && entity.getCombo() != null
                && entity.getCombo().getPrice() != null) {
            return entity.getCombo().getCurrencyCode();
        }
        return entity.getCurrencyCode();
    }
}
