package com.acomi.acomi_backend.meal.application.support;

import com.acomi.acomi_backend.meal.domain.model.MealPollOptionType;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealPollResponseEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MealPollChargeCalculator {

    private MealPollChargeCalculator() {}

    public static MealPollCharge fromResponses(List<MealPollResponseEntity> responses) {
        return fromResponses(responses, Map.of());
    }

    public static MealPollCharge fromResponses(
            List<MealPollResponseEntity> responses, Map<UUID, BigDecimal> itemDefaultPrices) {
        int mealCount = 0;
        BigDecimal currencyTotal = BigDecimal.ZERO;

        for (MealPollResponseEntity response : responses) {
            if (response.getSelectedOption().getOptionType() == MealPollOptionType.NOT_AVAILABLE
                    || response.getQuantity() <= 0) {
                continue;
            }
            if (response.getSelectedOption().getOptionType() != MealPollOptionType.MENU_ENTRY) {
                continue;
            }
            mealCount += response.getQuantity();
            BigDecimal unitPrice = MenuEntryPriceResolver.unitPrice(
                    response.getSelectedOption().getDailyMenuEntry(), itemDefaultPrices);
            if (unitPrice != null) {
                currencyTotal = currencyTotal.add(unitPrice.multiply(BigDecimal.valueOf(response.getQuantity())));
            }
        }

        return MealPollCharge.builder().mealCount(mealCount).currencyTotal(currencyTotal).build();
    }
}
