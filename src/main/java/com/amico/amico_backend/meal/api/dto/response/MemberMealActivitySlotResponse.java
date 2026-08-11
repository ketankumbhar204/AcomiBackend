package com.amico.amico_backend.meal.api.dto.response;

import com.amico.amico_backend.meal.domain.model.MealPollPaymentStatus;
import com.amico.amico_backend.meal.domain.model.MealType;
import com.amico.amico_backend.meal.domain.model.MemberMealActivitySlotStatus;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberMealActivitySlotResponse {

    private MealType mealType;
    private MemberMealActivitySlotStatus status;
    private String selectionLabel;
    private Integer quantity;
    private String deliveryLocationName;
    private BigDecimal slotAmount;
    private String currencyCode;
}
