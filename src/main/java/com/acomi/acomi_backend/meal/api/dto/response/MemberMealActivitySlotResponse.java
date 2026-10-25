package com.acomi.acomi_backend.meal.api.dto.response;

import com.acomi.acomi_backend.meal.domain.model.MealPollStatus;
import com.acomi.acomi_backend.meal.domain.model.MealType;
import com.acomi.acomi_backend.meal.domain.model.MemberMealActivitySlotStatus;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberMealActivitySlotResponse {

    private MealType mealType;
    private MemberMealActivitySlotStatus status;
    /** Effective poll availability. Separate from {@link #status}, which is the member response. */
    private MealPollStatus pollStatus;
    private String selectionLabel;
    private Integer quantity;
    private String deliveryLocationName;
    private BigDecimal slotAmount;
    private String currencyCode;
}
