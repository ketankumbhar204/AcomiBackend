package com.amico.amico_backend.meal.api.dto.response;

import com.amico.amico_backend.meal.domain.model.MealType;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberMealActivityDailyChargeResponse {

    private MealType mealType;
    private BigDecimal amount;
    private String currencyCode;
}
