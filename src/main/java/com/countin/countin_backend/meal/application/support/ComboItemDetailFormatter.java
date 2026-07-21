package com.countin.countin_backend.meal.application.support;

import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealComboItemEntity;
import java.util.List;
import java.util.stream.Collectors;

/** Formats combo item lines for customer-facing "Includes" / detail strings. */
public final class ComboItemDetailFormatter {

    private ComboItemDetailFormatter() {}

    public static String formatLine(String name, int quantity) {
        if (name == null || name.isBlank()) {
            return "";
        }
        int qty = quantity > 0 ? quantity : 1;
        if (qty <= 1) {
            return name;
        }
        return qty + " " + name;
    }

    public static String join(List<MealComboItemEntity> comboItems, String separator) {
        if (comboItems == null || comboItems.isEmpty()) {
            return "";
        }
        return comboItems.stream()
                .map(ci -> formatLine(ci.getItem().getName(), ci.getQuantity()))
                .filter(line -> !line.isBlank())
                .collect(Collectors.joining(separator));
    }
}
