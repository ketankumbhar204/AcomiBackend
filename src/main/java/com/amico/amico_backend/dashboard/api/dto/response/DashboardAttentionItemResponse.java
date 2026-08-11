package com.amico.amico_backend.dashboard.api.dto.response;

import com.amico.amico_backend.dashboard.domain.model.DashboardAttentionKind;
import com.amico.amico_backend.meal.domain.model.MealType;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardAttentionItemResponse {

    private DashboardAttentionKind kind;
    private Integer scheduledCount;
    private Integer totalMeals;
    private List<MealType> missingMealTypes;
    private Integer respondedCount;
    private Integer eligibleCount;
    private Integer openPollCount;
    private Integer overdueCount;
    private BigDecimal overdueAmount;
    private String currencyCode;
    private Integer pendingSubscriptionRequestCount;
}
