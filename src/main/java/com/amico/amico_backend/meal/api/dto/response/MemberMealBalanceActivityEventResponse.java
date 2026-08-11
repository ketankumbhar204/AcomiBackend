package com.amico.amico_backend.meal.api.dto.response;

import com.amico.amico_backend.meal.domain.model.MealBalanceLedgerEntryType;
import com.amico.amico_backend.meal.domain.model.MealSubscriptionAction;
import com.amico.amico_backend.meal.domain.model.MealType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberMealBalanceActivityEventResponse {

    private UUID eventId;
    private MealBalanceLedgerEntryType eventType;
    private BigDecimal meals;
    private BigDecimal paidAmount;
    private MealType mealType;
    private LocalDate pollDate;
    private String remarks;
    private BigDecimal balanceAfter;
    private LocalDateTime createdAt;
    private MealSubscriptionAction subscriptionAction;
}
