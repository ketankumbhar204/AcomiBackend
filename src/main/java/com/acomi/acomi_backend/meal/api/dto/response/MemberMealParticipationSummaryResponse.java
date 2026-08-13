package com.acomi.acomi_backend.meal.api.dto.response;

import com.acomi.acomi_backend.meal.domain.model.MealParticipationStatus;
import com.acomi.acomi_backend.meal.domain.model.MealPlanCode;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealParticipationEntity;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberMealParticipationSummaryResponse {

    private UUID participationId;
    private MealPlanCode mealPlanCode;
    private String mealPlanName;
    private MealParticipationStatus status;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private UUID defaultDeliveryLocationId;
    private String defaultDeliveryLocationName;

    public static MemberMealParticipationSummaryResponse from(MealParticipationEntity entity) {
        return MemberMealParticipationSummaryResponse.builder()
                .participationId(entity.getId())
                .mealPlanCode(entity.getMealPlan().getCode())
                .mealPlanName(entity.getMealPlan().getName())
                .status(entity.getStatus())
                .effectiveFrom(entity.getEffectiveFrom())
                .effectiveTo(entity.getEffectiveTo())
                .defaultDeliveryLocationId(
                        entity.getDefaultDeliveryLocation() != null
                                ? entity.getDefaultDeliveryLocation().getId()
                                : null)
                .defaultDeliveryLocationName(
                        entity.getDefaultDeliveryLocation() != null
                                ? entity.getDefaultDeliveryLocation().getName()
                                : null)
                .build();
    }
}
