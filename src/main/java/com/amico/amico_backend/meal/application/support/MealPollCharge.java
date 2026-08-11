package com.amico.amico_backend.meal.application.support;

import com.amico.amico_backend.space.domain.model.PrepaidBalanceUnit;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealPollCharge {

    private int mealCount;
    private BigDecimal currencyTotal;

    public BigDecimal requestedDebitAmount(PrepaidBalanceUnit unit) {
        if (unit == PrepaidBalanceUnit.MEALS) {
            return BigDecimal.valueOf(mealCount);
        }
        return currencyTotal;
    }
}
