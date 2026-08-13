package com.acomi.acomi_backend.meal.api.dto.response;

import com.acomi.acomi_backend.meal.domain.model.FoodType;
import com.acomi.acomi_backend.meal.domain.model.MealType;
import com.acomi.acomi_backend.meal.domain.model.MenuHistoryEntryType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MenuHistoryItemResponse {

    private UUID historyId;
    /** COMBO or ITEM */
    private MenuHistoryEntryType type;
    private MealType mealType;
    private String name;
    private String thumbnailUrl;
    private FoodType foodType;
    private String summary;
    private LocalDateTime lastUsedAt;
    private LocalDate lastUsedMenuDate;
    private int usageCount;
    private BigDecimal price;
    private String currencyCode;
    private UUID comboId;
    private UUID itemId;
    private List<UUID> itemIds;
}
