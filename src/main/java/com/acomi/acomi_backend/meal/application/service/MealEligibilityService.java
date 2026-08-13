package com.acomi.acomi_backend.meal.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.meal.api.dto.response.EligibleParticipantResponse;
import com.acomi.acomi_backend.meal.api.dto.response.MealEligibilityPlanBreakdownResponse;
import com.acomi.acomi_backend.meal.api.dto.response.MealEligibilitySlotResponse;
import com.acomi.acomi_backend.meal.api.dto.response.MealEligibilitySummaryResponse;
import com.acomi.acomi_backend.meal.domain.model.MealPlanCode;
import com.acomi.acomi_backend.meal.domain.model.MealType;
import com.acomi.acomi_backend.meal.domain.policy.MealOccupancyPolicy;
import com.acomi.acomi_backend.meal.domain.policy.MealPollEligibilityPolicy;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealParticipationEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MealParticipationRepository;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MealEligibilityService {

    private final MealParticipationRepository participationRepository;
    private final DailyMenuService dailyMenuService;
    private final MealAccessService mealAccessService;
    private final MealPollEligibilityPolicy pollEligibilityPolicy;
    private final MealOccupancyPolicy occupancyPolicy;
    private final SpaceRepository spaceRepository;

    @Transactional(readOnly = true)
    public MealEligibilitySummaryResponse getSummary(UUID spaceId, UUID callerId, LocalDate date) {
        mealAccessService.requireViewMeals(spaceId, callerId);
        LocalDate targetDate = date != null ? date : LocalDate.now();
        SpaceEntity space = loadSpace(spaceId);
        Set<UUID> occupiedMemberIds = occupancyPolicy
                .occupiedMemberIdsForDate(space, targetDate)
                .orElse(null);
        List<MealParticipationEntity> participations = participationRepository.findAllNonStoppedBySpaceId(spaceId);
        Set<MealType> publishedTypes = dailyMenuService.publishedMealTypes(spaceId, targetDate);
        List<MealEligibilitySlotResponse> slots = new ArrayList<>();
        Set<UUID> distinctEligibleMemberIds = new HashSet<>();

        for (MealType mealType : MealType.values()) {
            int eligibleCount = 0;
            int pausedCount = 0;
            Map<MealPlanCode, PlanAccumulator> byPlanCounts = new EnumMap<>(MealPlanCode.class);

            for (MealParticipationEntity participation : participations) {
                if (pollEligibilityPolicy.isPollEligible(participation, targetDate, mealType, occupiedMemberIds)) {
                    eligibleCount++;
                    distinctEligibleMemberIds.add(participation.getMember().getId());
                    MealPlanCode planCode = participation.getMealPlan().getCode();
                    byPlanCounts
                            .computeIfAbsent(planCode, code -> new PlanAccumulator(participation.getMealPlan().getName()))
                            .increment();
                } else if (pollEligibilityPolicy.isPausedPollAudienceMember(
                        participation, targetDate, mealType, occupiedMemberIds)) {
                    pausedCount++;
                }
            }

            List<MealEligibilityPlanBreakdownResponse> byPlan = byPlanCounts.entrySet().stream()
                    .map(entry -> MealEligibilityPlanBreakdownResponse.builder()
                            .mealPlanCode(entry.getKey())
                            .mealPlanName(entry.getValue().planName())
                            .count(entry.getValue().count())
                            .build())
                    .sorted(Comparator.comparing(response -> response.getMealPlanCode().name()))
                    .toList();

            slots.add(MealEligibilitySlotResponse.builder()
                    .mealType(mealType)
                    .eligibleCount(eligibleCount)
                    .pausedCount(pausedCount)
                    .published(publishedTypes.contains(mealType))
                    .byPlan(byPlan)
                    .build());
        }

        return MealEligibilitySummaryResponse.builder()
                .date(targetDate)
                .distinctEligibleMemberCount(distinctEligibleMemberIds.size())
                .slots(slots)
                .build();
    }

    @Transactional(readOnly = true)
    public List<EligibleParticipantResponse> listEligibleParticipants(
            UUID spaceId, UUID callerId, LocalDate date, MealType mealType) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        LocalDate targetDate = date != null ? date : LocalDate.now();
        SpaceEntity space = loadSpace(spaceId);
        Set<UUID> occupiedMemberIds = occupancyPolicy
                .occupiedMemberIdsForDate(space, targetDate)
                .orElse(null);
        return participationRepository.findAllActiveBySpaceId(spaceId).stream()
                .filter(participation ->
                        pollEligibilityPolicy.isPollEligible(participation, targetDate, mealType, occupiedMemberIds))
                .map(participation -> EligibleParticipantResponse.builder()
                        .memberId(participation.getMember().getId())
                        .memberName(participation.getMember().getFullName())
                        .mobileNumber(participation.getMember().getMobileNumber())
                        .mealPlanCode(participation.getMealPlan().getCode())
                        .mealPlanName(participation.getMealPlan().getName())
                        .build())
                .toList();
    }

    private SpaceEntity loadSpace(UUID spaceId) {
        return spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new BusinessException("Space not found", HttpStatus.NOT_FOUND));
    }

    private static final class PlanAccumulator {
        private final String planName;
        private int count;

        private PlanAccumulator(String planName) {
            this.planName = planName;
        }

        private void increment() {
            count++;
        }

        private String planName() {
            return planName;
        }

        private int count() {
            return count;
        }
    }
}
