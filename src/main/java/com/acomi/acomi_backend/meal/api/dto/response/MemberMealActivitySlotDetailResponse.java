package com.acomi.acomi_backend.meal.api.dto.response;

import com.acomi.acomi_backend.meal.domain.model.MealPollStatus;
import com.acomi.acomi_backend.meal.domain.model.MealType;
import com.acomi.acomi_backend.meal.domain.model.MemberMealActivitySlotStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberMealActivitySlotDetailResponse {

    private MealType mealType;
    private MemberMealActivitySlotStatus status;
    private boolean menuPublished;
    private MealPollStatus pollStatus;
    private String deliveryLocationName;
    private String deliveryLocationDescription;
    private LocalDateTime respondedAt;
    private BigDecimal slotTotal;
    private List<MemberMealActivitySelectionResponse> selections;
}
