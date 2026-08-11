package com.amico.amico_backend.meal.application.support;

import com.amico.amico_backend.meal.api.dto.response.MealPollOptionResponse;
import com.amico.amico_backend.meal.domain.model.MealPollOptionType;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealPollResponseEntity;
import java.math.BigDecimal;
import java.util.List;

public final class MealPollChargeCalculator {

    private MealPollChargeCalculator() {}

    public static MealPollCharge fromResponses(List<MealPollResponseEntity> responses) {
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
            MealPollOptionResponse optionMeta = MealPollOptionResponse.from(response.getSelectedOption());
            BigDecimal unitPrice = optionMeta.getPrice() != null ? optionMeta.getPrice() : BigDecimal.ZERO;
            currencyTotal = currencyTotal.add(unitPrice.multiply(BigDecimal.valueOf(response.getQuantity())));
        }

        return MealPollCharge.builder().mealCount(mealCount).currencyTotal(currencyTotal).build();
    }
}
