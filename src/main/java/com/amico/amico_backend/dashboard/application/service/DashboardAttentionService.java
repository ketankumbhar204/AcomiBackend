package com.amico.amico_backend.dashboard.application.service;

import com.amico.amico_backend.dashboard.api.dto.response.DashboardAttentionItemResponse;
import com.amico.amico_backend.dashboard.domain.model.DashboardAttentionKind;
import com.amico.amico_backend.meal.api.dto.response.MealEligibilitySummaryResponse;
import com.amico.amico_backend.meal.api.dto.response.MealPollResponse;
import com.amico.amico_backend.meal.application.service.MealAccessService;
import com.amico.amico_backend.meal.application.service.MealEligibilityService;
import com.amico.amico_backend.meal.application.service.MealPollService;
import com.amico.amico_backend.meal.domain.model.SubscriptionActivationRequestStatus;
import com.amico.amico_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.amico.amico_backend.meal.domain.model.DailyMenuStatus;
import com.amico.amico_backend.meal.domain.model.MealPollStatus;
import com.amico.amico_backend.meal.domain.model.MealType;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.DailyMenuEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.DailyMenuEntryRepository;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.DailyMenuRepository;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.SubscriptionActivationRequestRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardAttentionService {

    private static final List<MealType> MEAL_TYPES = List.of(MealType.values());

    private final DailyMenuRepository dailyMenuRepository;
    private final DailyMenuEntryRepository dailyMenuEntryRepository;
    private final MealEligibilityService mealEligibilityService;
    private final MealPollService mealPollService;
    private final MealAccessService mealAccessService;
    private final SubscriptionActivationRequestRepository subscriptionActivationRequestRepository;

    @Transactional(readOnly = true)
    public List<DashboardAttentionItemResponse> resolveAttention(
            UUID spaceId,
            UUID callerId,
            LocalDate tomorrow,
            int pendingPaymentsCount,
            BigDecimal overdueAmount,
            String currencyCode) {
        return resolveAttention(
                spaceId, callerId, tomorrow, pendingPaymentsCount, overdueAmount, currencyCode, null, null);
    }

    @Transactional(readOnly = true)
    public List<DashboardAttentionItemResponse> resolveAttention(
            UUID spaceId,
            UUID callerId,
            LocalDate tomorrow,
            int pendingPaymentsCount,
            BigDecimal overdueAmount,
            String currencyCode,
            MealEligibilitySummaryResponse sharedEligibility,
            List<MealPollResponse> sharedPolls) {
        List<DashboardAttentionItemResponse> attention = new ArrayList<>();

        resolveSubscriptionActivationAttention(spaceId, callerId).ifPresent(attention::add);
        resolveMenuAttention(spaceId, callerId, tomorrow, sharedEligibility, sharedPolls)
                .ifPresent(attention::add);

        if (pendingPaymentsCount > 0) {
            attention.add(DashboardAttentionItemResponse.builder()
                    .kind(DashboardAttentionKind.PAYMENTS_OVERDUE)
                    .overdueCount(pendingPaymentsCount)
                    .overdueAmount(overdueAmount)
                    .currencyCode(currencyCode)
                    .build());
        }

        return attention;
    }

    public List<DashboardAttentionItemResponse> resolvePaymentsAttention(
            int pendingPaymentsCount, java.math.BigDecimal overdueAmount, String currencyCode) {
        if (pendingPaymentsCount <= 0) {
            return List.of();
        }
        return List.of(DashboardAttentionItemResponse.builder()
                .kind(DashboardAttentionKind.PAYMENTS_OVERDUE)
                .overdueCount(pendingPaymentsCount)
                .overdueAmount(overdueAmount)
                .currencyCode(currencyCode)
                .build());
    }

    private Optional<DashboardAttentionItemResponse> resolveSubscriptionActivationAttention(
            UUID spaceId, UUID callerId) {
        SpaceMembershipEntity membership = mealAccessService.requireViewMeals(spaceId, callerId);
        if (!mealAccessService.canManageMeals(membership)) {
            return Optional.empty();
        }

        int pendingCount = subscriptionActivationRequestRepository
                .findBySpaceIdAndStatusOrderByCreatedAtDesc(
                        spaceId, SubscriptionActivationRequestStatus.PENDING)
                .size();
        if (pendingCount <= 0) {
            return Optional.empty();
        }

        return Optional.of(DashboardAttentionItemResponse.builder()
                .kind(DashboardAttentionKind.SUBSCRIPTION_ACTIVATION_PENDING)
                .pendingSubscriptionRequestCount(pendingCount)
                .build());
    }

    private Optional<DashboardAttentionItemResponse> resolveMenuAttention(
            UUID spaceId,
            UUID callerId,
            LocalDate tomorrow,
            MealEligibilitySummaryResponse sharedEligibility,
            List<MealPollResponse> sharedPolls) {
        SpaceMembershipEntity membership = mealAccessService.requireViewMeals(spaceId, callerId);
        if (!mealAccessService.canManageMeals(membership)) {
            return Optional.empty();
        }

        List<DailyMenuEntity> menus =
                dailyMenuRepository.findBySpaceAndDate(spaceId, tomorrow, false, DailyMenuStatus.DRAFT);
        MealEligibilitySummaryResponse eligibility = sharedEligibility != null
                ? sharedEligibility
                : mealEligibilityService.getSummary(spaceId, callerId, tomorrow);
        List<MealPollResponse> polls = sharedPolls != null
                ? sharedPolls
                : mealPollService.getPollsForDate(spaceId, callerId, tomorrow).getPolls();

        List<MealType> plannedTypes = MEAL_TYPES.stream()
                .filter(mealType -> isMealPlanned(menus, mealType))
                .toList();
        Set<MealType> missingMealTypes = EnumSet.copyOf(MEAL_TYPES);
        missingMealTypes.removeAll(plannedTypes);

        List<MealType> publishedTypes = MEAL_TYPES.stream()
                .filter(mealType -> isMealPublished(eligibility, mealType))
                .toList();

        List<MealPollResponse> openPolls = polls.stream()
                .filter(poll -> poll.getStatus() == MealPollStatus.OPEN)
                .toList();

        int eligibleCount = eligibility.getDistinctEligibleMemberCount();
        int respondedCount = openPolls.stream()
                .mapToInt(MealPollResponse::getResponseCount)
                .max()
                .orElse(0);

        DashboardAttentionItemResponse.DashboardAttentionItemResponseBuilder base =
                DashboardAttentionItemResponse.builder()
                        .scheduledCount(plannedTypes.size())
                        .totalMeals(MEAL_TYPES.size())
                        .missingMealTypes(new ArrayList<>(missingMealTypes))
                        .respondedCount(respondedCount)
                        .eligibleCount(eligibleCount)
                        .openPollCount(openPolls.size());

        if (plannedTypes.isEmpty()) {
            return Optional.of(base.kind(DashboardAttentionKind.NOT_PLANNED).build());
        }

        if (!missingMealTypes.isEmpty()) {
            return Optional.of(base.kind(DashboardAttentionKind.PARTIAL_PLANNED).build());
        }

        if (publishedTypes.size() < MEAL_TYPES.size()) {
            return Optional.of(base.kind(DashboardAttentionKind.READY_TO_SHARE).build());
        }

        if (!openPolls.isEmpty() && eligibleCount > 0 && respondedCount < eligibleCount) {
            return Optional.of(base.kind(DashboardAttentionKind.POLL_OPEN).build());
        }

        return Optional.empty();
    }

    private boolean isMealPlanned(List<DailyMenuEntity> menus, MealType mealType) {
        return menus.stream()
                .filter(menu -> menu.getMealType() == mealType)
                .findFirst()
                .map(menu -> dailyMenuEntryRepository.hasAvailableOptions(menu.getId()))
                .orElse(false);
    }

    private boolean isMealPublished(MealEligibilitySummaryResponse eligibility, MealType mealType) {
        return eligibility.getSlots().stream()
                .anyMatch(slot -> slot.getMealType() == mealType && slot.isPublished());
    }
}
