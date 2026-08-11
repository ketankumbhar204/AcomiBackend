package com.amico.amico_backend.meal.api.dto.response;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberSubscriptionLifetimeSummaryResponse {

    private BigDecimal totalMealsPurchased;
    private BigDecimal totalMealsConsumed;
    private BigDecimal totalAmountPaid;
    private long totalActivities;
}
