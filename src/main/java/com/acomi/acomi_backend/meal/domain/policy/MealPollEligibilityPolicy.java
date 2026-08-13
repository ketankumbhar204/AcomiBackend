package com.acomi.acomi_backend.meal.domain.policy;

import com.acomi.acomi_backend.meal.domain.model.MealType;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealParticipationEntity;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.time.LocalDate;
import java.util.Optional;
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
        return isPollEligible(participation, date, mealType, null, Optional.empty());
    }

    public boolean isPollEligible(
            MealParticipationEntity participation,
            LocalDate date,
            MealType mealType,
            Set<UUID> preloadedOccupiedMemberIds) {
        return isPollEligible(participation, date, mealType, preloadedOccupiedMemberIds, Optional.empty());
    }

    public boolean isPollEligible(
            MealParticipationEntity participation,
            LocalDate date,
            MealType mealType,
            Set<UUID> preloadedOccupiedMemberIds,
            Optional<OccupancyEntity> preloadedMemberOccupancy) {
        return isPollEligible(
                participation, date, mealType, preloadedOccupiedMemberIds, preloadedMemberOccupancy, false);
    }

    public boolean isPollEligible(
            MealParticipationEntity participation,
            LocalDate date,
            MealType mealType,
            Set<UUID> preloadedOccupiedMemberIds,
            Optional<OccupancyEntity> preloadedMemberOccupancy,
            boolean memberOccupancyPreloaded) {
        if (!MealEligibilityEngine.isEligibleForPollAudience(
                participation.getMember(), participation, date, mealType)) {
            return false;
        }
        SpaceEntity space = participation.getSpace();
        UUID memberId = participation.getMember().getId();
        if (preloadedOccupiedMemberIds != null) {
            if (!preloadedOccupiedMemberIds.contains(memberId)) {
                return false;
            }
        } else if (!occupancyPolicy.hasMemberOccupancyOnDate(
                space, preloadedMemberOccupancy, memberId, date, memberOccupancyPreloaded)) {
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
