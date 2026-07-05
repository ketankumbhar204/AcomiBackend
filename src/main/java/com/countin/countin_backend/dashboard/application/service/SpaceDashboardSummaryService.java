package com.countin.countin_backend.dashboard.application.service;

import com.countin.countin_backend.accommodation.domain.model.AccommodationStatus;
import com.countin.countin_backend.accommodation.infrastructure.persistence.repository.AccommodationSummaryRepository;
import com.countin.countin_backend.common.exception.ResourceNotFoundException;
import com.countin.countin_backend.dashboard.api.dto.response.DashboardAccommodationOperationsResponse;
import com.countin.countin_backend.dashboard.api.dto.response.DashboardAttentionItemResponse;
import com.countin.countin_backend.dashboard.api.dto.response.DashboardFinancialSummaryResponse;
import com.countin.countin_backend.dashboard.api.dto.response.DashboardMessOperationsResponse;
import com.countin.countin_backend.dashboard.api.dto.response.DashboardSummaryResponse;
import com.countin.countin_backend.dashboard.api.dto.response.MemberPaymentLedgerResponse;
import com.countin.countin_backend.meal.api.dto.response.MealHeadcountDayResponse;
import com.countin.countin_backend.meal.api.dto.response.MealHeadcountSlotResponse;
import com.countin.countin_backend.meal.api.dto.response.MealPollResponse;
import com.countin.countin_backend.meal.application.service.MealEligibilityService;
import com.countin.countin_backend.meal.application.service.MealHeadcountService;
import com.countin.countin_backend.meal.application.service.MealPollService;
import com.countin.countin_backend.meal.domain.model.MealPollStatus;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.DailyMenuEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.DailyMenuRepository;
import com.countin.countin_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.countin.countin_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import com.countin.countin_backend.space.domain.model.SpaceType;
import com.countin.countin_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.countin.countin_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpaceDashboardSummaryService {

    private final SpaceRepository spaceRepository;
    private final SpaceBillingService spaceBillingService;
    private final DashboardAttentionService dashboardAttentionService;
    private final MealEligibilityService mealEligibilityService;
    private final MealPollService mealPollService;
    private final MealHeadcountService mealHeadcountService;
    private final DailyMenuRepository dailyMenuRepository;
    private final AccommodationSummaryRepository accommodationSummaryRepository;
    private final OccupancyRepository occupancyRepository;

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(UUID spaceId, UUID callerId, String monthParam) {
        SpaceEntity space = spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));

        MemberPaymentLedgerResponse ledger = spaceBillingService.buildLedger(spaceId, callerId, monthParam);
        int pendingPaymentsCount = spaceBillingService.countPendingPayments(ledger.getMembers());

        DashboardFinancialSummaryResponse financial = ledger.getSummary();

        DashboardMessOperationsResponse messOperations = null;
        DashboardAccommodationOperationsResponse accommodationOperations = null;
        List<DashboardAttentionItemResponse> attention;
        if (space.getType() == SpaceType.MESS) {
            messOperations = buildMessOperations(spaceId, callerId, monthParam);
            attention = dashboardAttentionService.resolveAttention(
                    spaceId,
                    callerId,
                    LocalDate.now().plusDays(1),
                    pendingPaymentsCount,
                    financial.getPending(),
                    financial.getCurrencyCode());
        } else {
            if (isAccommodationApplicable(space.getType())) {
                accommodationOperations =
                        buildAccommodationOperations(spaceId, monthParam, pendingPaymentsCount);
            }
            attention = dashboardAttentionService.resolvePaymentsAttention(
                    pendingPaymentsCount, financial.getPending(), financial.getCurrencyCode());
        }

        return DashboardSummaryResponse.builder()
                .spaceType(space.getType())
                .month(ledger.getMonth())
                .financial(financial)
                .messOperations(messOperations)
                .accommodationOperations(accommodationOperations)
                .attention(attention)
                .build();
    }

    private DashboardMessOperationsResponse buildMessOperations(
            UUID spaceId, UUID callerId, String monthParam) {
        YearMonth month = YearMonth.parse(monthParam != null && !monthParam.isBlank()
                ? monthParam
                : YearMonth.now().toString());
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDate today = LocalDate.now();

        var eligibility = mealEligibilityService.getSummary(spaceId, callerId, tomorrow);
        List<MealPollResponse> polls = mealPollService.getPollsForDate(spaceId, callerId, tomorrow).getPolls();
        List<MealPollResponse> openPolls =
                polls.stream().filter(poll -> poll.getStatus() == MealPollStatus.OPEN).toList();

        List<DailyMenuEntity> menusPublished = dailyMenuRepository.findBySpaceAndDateRange(
                spaceId, month.atDay(1), month.atEndOfMonth(), true);

        MealHeadcountDayResponse headcount = mealHeadcountService.getDaySummary(spaceId, callerId, today);
        int todaysHeadcount = headcount.getSlots().stream()
                .mapToInt(MealHeadcountSlotResponse::getMealsToPrepare)
                .sum();

        int eligibleCount = eligibility.getDistinctEligibleMemberCount();
        int respondedCount = openPolls.stream()
                .mapToInt(MealPollResponse::getResponseCount)
                .max()
                .orElse(0);

        return DashboardMessOperationsResponse.builder()
                .membersReceivingMeals(eligibleCount)
                .menusPublishedThisMonth(menusPublished.size())
                .openPollsCount(openPolls.size())
                .todaysHeadcount(todaysHeadcount > 0 ? todaysHeadcount : null)
                .pollRespondedCount(respondedCount)
                .pollEligibleCount(eligibleCount)
                .build();
    }

    private DashboardAccommodationOperationsResponse buildAccommodationOperations(
            UUID spaceId, String monthParam, int pendingPaymentsCount) {
        YearMonth month = YearMonth.parse(monthParam != null && !monthParam.isBlank()
                ? monthParam
                : YearMonth.now().toString());
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();

        long occupiedBeds = accommodationSummaryRepository.countBedsByStatusForSpace(
                spaceId, AccommodationStatus.OCCUPIED);
        long vacantBeds = accommodationSummaryRepository.countBedsByStatusForSpace(
                spaceId, AccommodationStatus.AVAILABLE);

        List<OccupancyEntity> activeOccupancies = occupancyRepository.findActiveBySpaceId(spaceId);
        int moveInsThisMonth = (int) activeOccupancies.stream()
                .filter(occupancy -> {
                    LocalDate moveIn = occupancy.getMoveInDate();
                    return moveIn != null && !moveIn.isBefore(monthStart) && !moveIn.isAfter(monthEnd);
                })
                .count();

        return DashboardAccommodationOperationsResponse.builder()
                .occupiedBeds((int) occupiedBeds)
                .vacantBeds((int) vacantBeds)
                .moveInsThisMonth(moveInsThisMonth)
                .pendingPaymentsCount(pendingPaymentsCount)
                .build();
    }

    private boolean isAccommodationApplicable(SpaceType spaceType) {
        return spaceType != SpaceType.MESS;
    }
}
