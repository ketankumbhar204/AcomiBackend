package com.amico.amico_backend.meal.domain.policy;

import com.amico.amico_backend.meal.domain.model.MealParticipationStatus;
import com.amico.amico_backend.meal.domain.model.MealType;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealParticipationEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealPlanEntity;
import com.amico.amico_backend.member.domain.model.MemberStatus;
import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberEntity;
import java.time.LocalDate;

public final class MealEligibilityEngine {

    private MealEligibilityEngine() {}

    public static boolean mealPlanCovers(MealPlanEntity plan, MealType mealType) {
        return switch (plan.getCode()) {
            case NONE -> false;
            case FULL -> true;
            case BREAKFAST -> mealType == MealType.BREAKFAST;
            case LUNCH -> mealType == MealType.LUNCH;
            case DINNER -> mealType == MealType.DINNER;
            case CUSTOM -> switch (mealType) {
                case BREAKFAST -> plan.isBreakfastIncluded();
                case LUNCH -> plan.isLunchIncluded();
                case DINNER -> plan.isDinnerIncluded();
            };
        };
    }

    public static boolean isEligibleForPollAudience(
            MemberEntity member, MealParticipationEntity participation, LocalDate date, MealType mealType) {
        if (member.getStatus() != MemberStatus.ACTIVE || !member.isActive()) {
            return false;
        }
        if (participation.getStatus() != MealParticipationStatus.ACTIVE) {
            return false;
        }
        if (participation.getEffectiveFrom().isAfter(date)) {
            return false;
        }
        if (participation.getEffectiveTo() != null && participation.getEffectiveTo().isBefore(date)) {
            return false;
        }
        return mealPlanCovers(participation.getMealPlan(), mealType);
    }

    public static boolean isPausedPollAudienceMember(
            MemberEntity member, MealParticipationEntity participation, LocalDate date, MealType mealType) {
        if (member.getStatus() != MemberStatus.ACTIVE || !member.isActive()) {
            return false;
        }
        if (participation.getStatus() != MealParticipationStatus.PAUSED) {
            return false;
        }
        if (participation.getEffectiveFrom().isAfter(date)) {
            return false;
        }
        if (participation.getEffectiveTo() != null && participation.getEffectiveTo().isBefore(date)) {
            return false;
        }
        return mealPlanCovers(participation.getMealPlan(), mealType);
    }
}
