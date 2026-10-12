package com.acomi.acomi_backend.meal.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.meal.domain.model.DailyMenuEntryType;
import com.acomi.acomi_backend.meal.domain.model.MealPollOptionType;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.DailyMenuEntryEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.FoodItemEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealComboEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealPollOptionEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealPollResponseEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.SpaceFoodItemSettingsEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MenuEntryPriceResolverTest {

    @Test
    void itemUsesSpaceCatalogPriceWhenMenuEntryPriceIsEmpty() {
        UUID itemId = UUID.randomUUID();
        FoodItemEntity item = new FoodItemEntity();
        item.setId(itemId);
        DailyMenuEntryEntity entry = DailyMenuEntryEntity.builder()
                .entryType(DailyMenuEntryType.ITEM)
                .item(item)
                .label("Boiled Egg")
                .build();
        Map<UUID, BigDecimal> defaults = MenuEntryPriceResolver.itemDefaults(List.of(
                SpaceFoodItemSettingsEntity.builder()
                        .itemId(itemId)
                        .defaultPrice(new BigDecimal("40.00"))
                        .build()));

        assertThat(MenuEntryPriceResolver.unitPrice(entry, defaults)).isEqualByComparingTo("40.00");
    }

    @Test
    void comboUsesComboPriceAndDepositLikeZeroIsNotAPrice() {
        MealComboEntity combo = MealComboEntity.builder()
                .name("Standard Lunch Thali")
                .price(new BigDecimal("120.00"))
                .currencyCode("INR")
                .build();
        DailyMenuEntryEntity entry = DailyMenuEntryEntity.builder()
                .entryType(DailyMenuEntryType.COMBO)
                .combo(combo)
                .label("Standard Lunch Thali")
                .price(null)
                .build();

        assertThat(MenuEntryPriceResolver.unitPrice(entry, Map.of())).isEqualByComparingTo("120.00");
        assertThat(MenuEntryPriceResolver.unitPrice(
                        DailyMenuEntryEntity.builder()
                                .entryType(DailyMenuEntryType.ITEM)
                                .label("Unpriced")
                                .build(),
                        Map.of()))
                .isNull();
    }

    @Test
    void chargeSumsPricedMealsAndSkipsNotAvailable() {
        UUID eggId = UUID.randomUUID();
        FoodItemEntity egg = new FoodItemEntity();
        egg.setId(eggId);
        MealPollResponseEntity breakfast = response(
                DailyMenuEntryEntity.builder()
                        .entryType(DailyMenuEntryType.ITEM)
                        .item(egg)
                        .label("Boiled Egg")
                        .build(),
                1);
        MealPollResponseEntity lunch = response(
                DailyMenuEntryEntity.builder()
                        .entryType(DailyMenuEntryType.ITEM)
                        .label("Not available")
                        .build(),
                1);
        lunch.getSelectedOption().setOptionType(MealPollOptionType.NOT_AVAILABLE);
        MealPollResponseEntity dinner = response(
                DailyMenuEntryEntity.builder()
                        .entryType(DailyMenuEntryType.PACKAGE)
                        .label("Dinner plate")
                        .price(new BigDecimal("80.00"))
                        .build(),
                2);

        MealPollCharge charge = MealPollChargeCalculator.fromResponses(
                List.of(breakfast, lunch, dinner),
                MenuEntryPriceResolver.itemDefaults(List.of(SpaceFoodItemSettingsEntity.builder()
                        .itemId(eggId)
                        .defaultPrice(new BigDecimal("40"))
                        .build())));

        assertThat(charge.getMealCount()).isEqualTo(3);
        assertThat(charge.getCurrencyTotal()).isEqualByComparingTo("200");
    }

    private static MealPollResponseEntity response(DailyMenuEntryEntity entry, int quantity) {
        return MealPollResponseEntity.builder()
                .selectedOption(MealPollOptionEntity.builder()
                        .optionType(MealPollOptionType.MENU_ENTRY)
                        .dailyMenuEntry(entry)
                        .label(entry.getLabel())
                        .build())
                .quantity(quantity)
                .build();
    }
}
