package com.acomi.acomi_backend.meal.api.dto.response;

import com.acomi.acomi_backend.meal.domain.model.DailyMenuEntryType;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.DailyMenuEntryEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.DailyMenuPackageItemEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DailyMenuOptionResponse {

    private UUID optionId;
    private DailyMenuEntryType entryType;
    private UUID comboId;
    private UUID itemId;
    private String label;
    private int sortOrder;
    private BigDecimal price;
    private String currencyCode;
    private List<DailyMenuPackageItemResponse> packageItems;

    @JsonProperty("isAvailable")
    private boolean available;

    @JsonProperty("isExtra")
    private boolean extra;

    public static DailyMenuOptionResponse from(DailyMenuEntryEntity entity) {
        return from(entity, Collections.emptyList());
    }

    public static DailyMenuOptionResponse from(
            DailyMenuEntryEntity entity, List<DailyMenuPackageItemEntity> packageItems) {
        List<DailyMenuPackageItemResponse> items = packageItems.stream()
                .map(pi -> DailyMenuPackageItemResponse.builder()
                        .itemId(pi.getItem().getId())
                        .name(pi.getItem().getName())
                        .build())
                .toList();
        return DailyMenuOptionResponse.builder()
                .optionId(entity.getId())
                .entryType(entity.getEntryType())
                .comboId(entity.getCombo() != null ? entity.getCombo().getId() : null)
                .itemId(entity.getItem() != null ? entity.getItem().getId() : null)
                .label(entity.getLabel())
                .sortOrder(entity.getSortOrder())
                .price(resolvePrice(entity))
                .currencyCode(resolveCurrencyCode(entity))
                .available(entity.isAvailable())
                .extra(entity.isExtra())
                .packageItems(items.isEmpty() ? null : items)
                .build();
    }

    private static BigDecimal resolvePrice(DailyMenuEntryEntity entity) {
        if (entity.getEntryType() == DailyMenuEntryType.COMBO
                && entity.getCombo() != null
                && entity.getCombo().getPrice() != null) {
            return entity.getCombo().getPrice();
        }
        return entity.getPrice();
    }

    private static String resolveCurrencyCode(DailyMenuEntryEntity entity) {
        if (entity.getEntryType() == DailyMenuEntryType.COMBO
                && entity.getCombo() != null
                && entity.getCombo().getPrice() != null) {
            return entity.getCombo().getCurrencyCode();
        }
        return entity.getCurrencyCode();
    }
}
