package com.amico.amico_backend.meal.api.dto.response;

import com.amico.amico_backend.meal.domain.model.MealParticipationStatus;
import com.amico.amico_backend.meal.domain.model.MealPlanCode;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealParticipationEntity;
import com.amico.amico_backend.member.domain.model.MembershipRole;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealParticipationResponse {

    private UUID participationId;
    private UUID memberId;
    private String memberName;
    private MembershipRole memberRole;
    private UUID mealPlanId;
    private MealPlanCode mealPlanCode;
    private String mealPlanName;
    private MealParticipationStatus status;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private UUID sourceOccupancyId;
    private UUID defaultDeliveryLocationId;
    private String defaultDeliveryLocationName;

    public static MealParticipationResponse from(MealParticipationEntity entity) {
        return MealParticipationResponse.builder()
                .participationId(entity.getId())
                .memberId(entity.getMember().getId())
                .memberName(entity.getMember().getFullName())
                .memberRole(entity.getMember().getRole())
                .mealPlanId(entity.getMealPlan().getId())
                .mealPlanCode(entity.getMealPlan().getCode())
                .mealPlanName(entity.getMealPlan().getName())
                .status(entity.getStatus())
                .effectiveFrom(entity.getEffectiveFrom())
                .effectiveTo(entity.getEffectiveTo())
                .sourceOccupancyId(
                        entity.getSourceOccupancy() != null ? entity.getSourceOccupancy().getId() : null)
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
