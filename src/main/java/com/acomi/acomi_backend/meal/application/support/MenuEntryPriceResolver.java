package com.acomi.acomi_backend.meal.application.support;

import com.acomi.acomi_backend.meal.domain.model.DailyMenuEntryType;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.DailyMenuEntryEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.SpaceFoodItemSettingsEntity;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Unit price for a selected menu line.
 * Combos use the combo price. Packages use the entry price.
 * Items use the entry price when one was stored, otherwise the space catalog default.
 */
public final class MenuEntryPriceResolver {

    private MenuEntryPriceResolver() {}

    public static Map<UUID, BigDecimal> itemDefaults(List<SpaceFoodItemSettingsEntity> rows) {
        Map<UUID, BigDecimal> prices = new HashMap<>();
        if (rows == null) {
            return prices;
        }
        for (SpaceFoodItemSettingsEntity row : rows) {
            if (row.getItemId() != null && isPositive(row.getDefaultPrice())) {
                prices.put(row.getItemId(), row.getDefaultPrice());
            }
        }
        return prices;
    }

    public static BigDecimal unitPrice(DailyMenuEntryEntity entry, Map<UUID, BigDecimal> itemDefaults) {
        if (entry == null) {
            return null;
        }
        if (entry.getEntryType() == DailyMenuEntryType.COMBO
                && entry.getCombo() != null
                && isPositive(entry.getCombo().getPrice())) {
            return entry.getCombo().getPrice();
        }
        if (isPositive(entry.getPrice())) {
            return entry.getPrice();
        }
        if (entry.getEntryType() == DailyMenuEntryType.ITEM
                && entry.getItem() != null
                && entry.getItem().getId() != null
                && itemDefaults != null) {
            BigDecimal catalogPrice = itemDefaults.get(entry.getItem().getId());
            if (isPositive(catalogPrice)) {
                return catalogPrice;
            }
        }
        return null;
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}
