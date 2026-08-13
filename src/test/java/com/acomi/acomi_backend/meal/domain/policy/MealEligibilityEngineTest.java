package com.acomi.acomi_backend.meal.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.meal.domain.model.MealParticipationStatus;
import com.acomi.acomi_backend.meal.domain.model.MealPlanCode;
import com.acomi.acomi_backend.meal.domain.model.MealType;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealParticipationEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealPlanEntity;
import com.acomi.acomi_backend.member.domain.model.MemberStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MealEligibilityEngineTest {

    @Test
    void mealPlanCovers_breakfastCodeCoversBreakfastOnly() {
        MealPlanEntity breakfastPlan = MealPlanEntity.builder()
                .code(MealPlanCode.BREAKFAST)
                .breakfastIncluded(true)
                .lunchIncluded(false)
                .dinnerIncluded(false)
                .build();

        assertThat(MealEligibilityEngine.mealPlanCovers(breakfastPlan, MealType.BREAKFAST)).isTrue();
        assertThat(MealEligibilityEngine.mealPlanCovers(breakfastPlan, MealType.LUNCH)).isFalse();
    }

    @Test
    void mealPlanCovers_customPlanUsesInclusionFlags() {
        MealPlanEntity customLunchOnly = MealPlanEntity.builder()
                .code(MealPlanCode.CUSTOM)
                .breakfastIncluded(false)
                .lunchIncluded(true)
                .dinnerIncluded(false)
                .build();

        assertThat(MealEligibilityEngine.mealPlanCovers(customLunchOnly, MealType.LUNCH)).isTrue();
        assertThat(MealEligibilityEngine.mealPlanCovers(customLunchOnly, MealType.DINNER)).isFalse();
    }

    @Test
    void isEligibleForPollAudience_fullPlanCoversAllMealTypes() {
        MemberEntity member = activeMember();
        MealParticipationEntity participation = participation(fullPlan(), MealParticipationStatus.ACTIVE);

        assertThat(MealEligibilityEngine.isEligibleForPollAudience(
                        member, participation, LocalDate.now(), MealType.BREAKFAST))
                .isTrue();
        assertThat(MealEligibilityEngine.isEligibleForPollAudience(
                        member, participation, LocalDate.now(), MealType.DINNER))
                .isTrue();
    }

    @Test
    void isEligibleForPollAudience_lunchOnlyPlanExcludesDinner() {
        MemberEntity member = activeMember();
        MealPlanEntity lunchPlan = MealPlanEntity.builder()
                .code(MealPlanCode.LUNCH)
                .breakfastIncluded(false)
                .lunchIncluded(true)
                .dinnerIncluded(false)
                .build();
        MealParticipationEntity participation = participation(lunchPlan, MealParticipationStatus.ACTIVE);

        assertThat(MealEligibilityEngine.isEligibleForPollAudience(
                        member, participation, LocalDate.now(), MealType.LUNCH))
                .isTrue();
        assertThat(MealEligibilityEngine.isEligibleForPollAudience(
                        member, participation, LocalDate.now(), MealType.DINNER))
                .isFalse();
    }

    @Test
    void isEligibleForPollAudience_pausedParticipationIsExcluded() {
        MemberEntity member = activeMember();
        MealParticipationEntity participation = participation(fullPlan(), MealParticipationStatus.PAUSED);

        assertThat(MealEligibilityEngine.isEligibleForPollAudience(
                        member, participation, LocalDate.now(), MealType.LUNCH))
                .isFalse();
    }

    @Test
    void isPausedPollAudienceMember_countsPausedMembersWithMatchingPlan() {
        MemberEntity member = activeMember();
        MealParticipationEntity participation = participation(fullPlan(), MealParticipationStatus.PAUSED);

        assertThat(MealEligibilityEngine.isPausedPollAudienceMember(
                        member, participation, LocalDate.now(), MealType.LUNCH))
                .isTrue();
        assertThat(MealEligibilityEngine.isPausedPollAudienceMember(
                        member, participation, LocalDate.now(), MealType.BREAKFAST))
                .isTrue();
    }

    private static MemberEntity activeMember() {
        return MemberEntity.builder().status(MemberStatus.ACTIVE).isActive(true).build();
    }

    private static MealPlanEntity fullPlan() {
        return MealPlanEntity.builder()
                .code(MealPlanCode.FULL)
                .breakfastIncluded(true)
                .lunchIncluded(true)
                .dinnerIncluded(true)
                .build();
    }

    private static MealParticipationEntity participation(MealPlanEntity plan, MealParticipationStatus status) {
        return MealParticipationEntity.builder()
                .mealPlan(plan)
                .status(status)
                .effectiveFrom(LocalDate.now().minusDays(1))
                .build();
    }
}
