package com.countin.countin_backend.meal.domain.policy;

import com.countin.countin_backend.meal.domain.model.MealType;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealParticipationEntity;
import com.countin.countin_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MealPollEligibilityPolicy {

    private final MemberSubscriptionPolicy subscriptionPolicy;
    private final MealOccupancyPolicy occupancyPolicy;

    public boolean isPollEligible(MealParticipationEntity participation, LocalDate date, MealType mealType) {
        return isPollEligible(participation, date, mealType, null);
    }

    public boolean isPollEligible(
            MealParticipationEntity participation,
            LocalDate date,
            MealType mealType,
            Set<UUID> preloadedOccupiedMemberIds) {
        if (!MealEligibilityEngine.isEligibleForPollAudience(
                participation.getMember(), participation, date, mealType)) {
            return false;
        }
        SpaceEntity space = participation.getSpace();
        if (!occupancyPolicy.hasOccupancyOnDate(
                space, participation.getMember().getId(), date, preloadedOccupiedMemberIds)) {
            return false;
        }
        return subscriptionPolicy.canParticipateInPolls(space, participation.getMember());
    }

    public boolean isPausedPollAudienceMember(
            MealParticipationEntity participation, LocalDate date, MealType mealType) {
        return isPausedPollAudienceMember(participation, date, mealType, null);
    }

    public boolean isPausedPollAudienceMember(
            MealParticipationEntity participation,
            LocalDate date,
            MealType mealType,
            Set<UUID> preloadedOccupiedMemberIds) {
        if (!MealEligibilityEngine.isPausedPollAudienceMember(
                participation.getMember(), participation, date, mealType)) {
            return false;
        }
        SpaceEntity space = participation.getSpace();
        return occupancyPolicy.hasOccupancyOnDate(
                space, participation.getMember().getId(), date, preloadedOccupiedMemberIds);
    }
}
