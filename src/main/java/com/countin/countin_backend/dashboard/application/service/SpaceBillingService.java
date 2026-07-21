package com.countin.countin_backend.dashboard.application.service;

import com.countin.countin_backend.common.exception.BusinessException;
import com.countin.countin_backend.common.exception.ResourceNotFoundException;
import com.countin.countin_backend.dashboard.api.dto.response.DashboardFinancialSummaryResponse;
import com.countin.countin_backend.dashboard.api.dto.response.MemberPaymentLedgerResponse;
import com.countin.countin_backend.dashboard.api.dto.response.MemberPaymentLedgerRowResponse;
import com.countin.countin_backend.dashboard.api.dto.response.PrepaidBalanceSummaryResponse;
import com.countin.countin_backend.dashboard.application.support.MealLedgerContribution;
import com.countin.countin_backend.dashboard.application.support.OccupancyBillingCalculator;
import com.countin.countin_backend.dashboard.application.support.PayPerMealBillingCalculator;
import com.countin.countin_backend.dashboard.application.support.PrepaidBalanceBillingCalculator;
import com.countin.countin_backend.dashboard.application.support.SpacePaymentLedgerSupport;
import com.countin.countin_backend.dashboard.domain.model.DashboardFinancialSource;
import com.countin.countin_backend.dashboard.domain.model.MemberPaymentStatus;
import com.countin.countin_backend.payment.application.support.PaymentMonthCountsSupport;
import com.countin.countin_backend.meal.application.support.MealBillingResolver;
import com.countin.countin_backend.meal.application.support.MealPricingPolicy;
import com.countin.countin_backend.meal.domain.model.MealParticipationStatus;
import com.countin.countin_backend.meal.infrastructure.persistence.entity.MealParticipationEntity;
import com.countin.countin_backend.meal.infrastructure.persistence.repository.MealParticipationRepository;
import com.countin.countin_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.countin.countin_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.countin.countin_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.countin.countin_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import com.countin.countin_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.countin.countin_backend.space.domain.model.MealBillingType;
import com.countin.countin_backend.space.domain.model.PrepaidBalanceUnit;
import com.countin.countin_backend.space.domain.model.SpaceType;
import com.countin.countin_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.countin.countin_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
public class SpaceBillingService {

    private static final String DEFAULT_CURRENCY = "INR";

    private final SpaceRepository spaceRepository;
    private final MemberRepository memberRepository;
    private final MealParticipationRepository participationRepository;
    private final OccupancyRepository occupancyRepository;
    private final PayPerMealBillingCalculator payPerMealBillingCalculator;
    private final PrepaidBalanceBillingCalculator prepaidBalanceBillingCalculator;
    private final MealBillingResolver mealBillingResolver;
    private final SpacePaymentLedgerSupport spacePaymentLedgerSupport;

    @Transactional(readOnly = true)
    public MemberPaymentLedgerResponse buildLedger(UUID spaceId, UUID callerId, String monthParam) {
        SpaceEntity space = loadSpace(spaceId);
        YearMonth month = parseMonth(monthParam);

        List<MealParticipationEntity> participations =
                participationRepository.findAllNonStoppedBySpaceId(spaceId).stream()
                        .filter(participation -> participation.getStatus() == MealParticipationStatus.ACTIVE)
                        .toList();
        List<OccupancyEntity> activeOccupancies = isAccommodationApplicable(space.getType())
                ? loadBillableOccupancies(spaceId, month)
                : List.of();

        Set<UUID> memberIds = collectRelevantMemberIds(space.getType(), participations, activeOccupancies);
        Map<UUID, MemberEntity> membersById = loadMembers(spaceId, memberIds);
        Map<UUID, OccupancyEntity> occupancyByMember = mapOccupancyByMember(activeOccupancies);
        Map<UUID, List<SpacePaymentEntity>> paymentsByMember =
                spacePaymentLedgerSupport.loadPaymentsByMember(spaceId, month.toString());

        List<MemberPaymentLedgerRowResponse> rows = new ArrayList<>();
        for (UUID memberId : memberIds) {
            MemberEntity member = membersById.get(memberId);
            if (member == null) {
                continue;
            }
            rows.add(buildRow(
                    space,
                    member,
                    month,
                    callerId,
                    participations.stream().anyMatch(row -> row.getMember().getId().equals(memberId)),
                    occupancyByMember.get(memberId),
                    paymentsByMember.getOrDefault(memberId, List.of())));
        }

        rows.sort(Comparator.comparing(MemberPaymentLedgerRowResponse::getPending, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(MemberPaymentLedgerRowResponse::getMemberName, String.CASE_INSENSITIVE_ORDER));

        DashboardFinancialSummaryResponse summary =
                aggregateRows(rows, space, month, !participations.isEmpty());

        return MemberPaymentLedgerResponse.builder()
                .month(month.toString())
                .spaceType(space.getType())
                .summary(summary)
                .members(rows)
                .build();
    }

    @Transactional(readOnly = true)
    public DashboardFinancialSummaryResponse buildSpaceFinancialSummary(
            UUID spaceId, UUID callerId, String monthParam) {
        return buildLedger(spaceId, callerId, monthParam).getSummary();
    }

    /** Members who still need customer payment action (excludes under-review / owner queue). */
    public int countPendingPayments(List<MemberPaymentLedgerRowResponse> rows) {
        return PaymentMonthCountsSupport.countPendingMembers(rows);
    }

    private MemberPaymentLedgerRowResponse buildRow(
            SpaceEntity space,
            MemberEntity member,
            YearMonth month,
            UUID callerId,
            boolean hasMealParticipation,
            OccupancyEntity occupancy,
            List<SpacePaymentEntity> memberPayments) {
        UUID memberId = member.getId();
        UUID spaceId = space.getId();
        boolean isMess = space.getType() == SpaceType.MESS;
        boolean accommodationApplicable = isAccommodationApplicable(space.getType());
        MealBillingType memberBillingType = mealBillingResolver.resolve(space, member);

        BigDecimal expectedCharges = BigDecimal.ZERO;
        BigDecimal collected = BigDecimal.ZERO;
        boolean hasExpected = false;
        boolean hasCollected = false;
        String currencyCode = DEFAULT_CURRENCY;

        if (MealPricingPolicy.usesSeparateMealBilling(space) && (isMess || hasMealParticipation)) {
            MealLedgerContribution mealContribution = computeMealContribution(
                    space, memberBillingType, spaceId, memberId, callerId, month);
            if (mealContribution.getExpected() != null) {
                expectedCharges = expectedCharges.add(mealContribution.getExpected());
                hasExpected = true;
            }
            // After MealDaySpacePaymentBridge, MEAL space_payments are the payment SoT — skip
            // meal-activity paidAmount to avoid double-counting approved day payments.
            boolean hasMealSpacePayments = spacePaymentLedgerSupport.hasMealSpacePayments(memberPayments);
            if (!hasMealSpacePayments && mealContribution.getCollected() != null) {
                collected = collected.add(mealContribution.getCollected());
                hasCollected = true;
            }
            if (mealContribution.getCurrencyCode() != null) {
                currencyCode = mealContribution.getCurrencyCode();
            }
        }

        if (occupancy != null && accommodationApplicable) {
            BigDecimal occupancyExpected =
                    OccupancyBillingCalculator.computeMonthlyExpected(occupancy, month);
            if (occupancyExpected != null) {
                expectedCharges = expectedCharges.add(occupancyExpected);
                hasExpected = true;
            }
        }

        BigDecimal paymentCollected = spacePaymentLedgerSupport.sumPaidAmount(memberPayments);
        if (paymentCollected.compareTo(BigDecimal.ZERO) > 0) {
            collected = collected.add(paymentCollected);
            hasCollected = true;
        }

        BigDecimal underReviewAmount = spacePaymentLedgerSupport.sumUnderReviewAmount(memberPayments);
        BigDecimal expected = hasExpected ? expectedCharges : null;
        BigDecimal collectedAmount = hasCollected ? collected : null;
        BigDecimal underReview =
                underReviewAmount.compareTo(BigDecimal.ZERO) > 0 ? underReviewAmount : null;
        BigDecimal pending =
                spacePaymentLedgerSupport.computePendingAmount(expected, collectedAmount, underReview);

        MemberPaymentLedgerRowResponse.MemberPaymentLedgerRowResponseBuilder rowBuilder =
                MemberPaymentLedgerRowResponse.builder()
                        .memberId(memberId)
                        .memberName(member.getFullName())
                        .expectedCharges(expected)
                        .collected(collectedAmount)
                        .underReview(underReview)
                        .pending(pending)
                        .currencyCode(currencyCode)
                        .status(spacePaymentLedgerSupport.resolveRowStatus(
                                expected, collectedAmount, underReview, memberPayments))
                        .mealBillingType(memberBillingType);

        if (memberBillingType == MealBillingType.PREPAID_BALANCE) {
            PrepaidBalanceBillingCalculator.MemberMonthlyBalance monthlyBalance =
                    prepaidBalanceBillingCalculator.memberMonthlyBalance(spaceId, memberId, month);
            PrepaidBalanceUnit unit = space.getPrepaidBalanceUnit() != null
                    ? space.getPrepaidBalanceUnit()
                    : PrepaidBalanceUnit.MEALS;
            rowBuilder
                    .mealBalanceRemaining(monthlyBalance.remaining())
                    .mealBalancePurchased(monthlyBalance.purchased())
                    .mealBalanceConsumed(monthlyBalance.consumed())
                    .mealBalanceUnit(unit);
        }

        return rowBuilder.build();
    }

    private MealLedgerContribution computeMealContribution(
            SpaceEntity space,
            MealBillingType memberBillingType,
            UUID spaceId,
            UUID memberId,
            UUID callerId,
            YearMonth month) {
        if (memberBillingType == MealBillingType.PREPAID_BALANCE) {
            return prepaidBalanceBillingCalculator.computeMemberContribution(
                    space, spaceId, memberId, callerId, month, payPerMealBillingCalculator);
        }
        return payPerMealBillingCalculator.computeMemberContribution(spaceId, memberId, callerId, month);
    }

    private DashboardFinancialSummaryResponse aggregateRows(
            List<MemberPaymentLedgerRowResponse> rows,
            SpaceEntity space,
            YearMonth month,
            boolean hasMealParticipation) {
        boolean messSpace = space.getType() == SpaceType.MESS;
        boolean hasPrepaidMembers = messSpace
                && rows.stream().anyMatch(row -> row.getMealBillingType() == MealBillingType.PREPAID_BALANCE);
        boolean hasPayPerMealMembers = messSpace
                && rows.stream().anyMatch(row -> row.getMealBillingType() != MealBillingType.PREPAID_BALANCE);
        boolean mixedMealBilling = hasPrepaidMembers && hasPayPerMealMembers;

        PrepaidBalanceSummaryResponse prepaidBalance = hasPrepaidMembers
                ? prepaidBalanceBillingCalculator.aggregateSpaceSummary(space, month)
                : null;

        BigDecimal expected;
        BigDecimal collected;
        BigDecimal underReview;
        if (messSpace && hasPrepaidMembers && !hasPayPerMealMembers) {
            expected = null;
            collected = prepaidBalance != null ? prepaidBalance.getAmountCollected() : null;
            underReview = null;
        } else {
            List<MemberPaymentLedgerRowResponse> payPerMealRows = messSpace
                    ? rows.stream()
                            .filter(row -> row.getMealBillingType() != MealBillingType.PREPAID_BALANCE)
                            .toList()
                    : rows;
            expected = sumNullable(
                    payPerMealRows.stream().map(MemberPaymentLedgerRowResponse::getExpectedCharges).toList());
            collected = sumNullable(
                    payPerMealRows.stream().map(MemberPaymentLedgerRowResponse::getCollected).toList());
            underReview = sumNullable(
                    payPerMealRows.stream().map(MemberPaymentLedgerRowResponse::getUnderReview).toList());
        }

        String currencyCode = rows.stream()
                .map(MemberPaymentLedgerRowResponse::getCurrencyCode)
                .filter(code -> code != null && !code.isBlank())
                .findFirst()
                .orElse(prepaidBalance != null && prepaidBalance.getCurrencyCode() != null
                        ? prepaidBalance.getCurrencyCode()
                        : DEFAULT_CURRENCY);

        return DashboardFinancialSummaryResponse.builder()
                .expectedCharges(expected)
                .collected(collected)
                .underReview(underReview)
                .pending(spacePaymentLedgerSupport.computePendingAmount(expected, collected, underReview))
                .currencyCode(currencyCode)
                .source(resolveSource(space, hasMealParticipation))
                .mealBillingType(space.getMealBillingType())
                .prepaidBalance(prepaidBalance)
                .mixedMealBilling(mixedMealBilling ? true : null)
                .build();
    }

    private DashboardFinancialSource resolveSource(SpaceEntity space, boolean hasMealParticipation) {
        SpaceType spaceType = space.getType();
        if (spaceType == SpaceType.MESS) {
            return DashboardFinancialSource.MEAL_ACTIVITY;
        }
        if (hasMealParticipation
                && isAccommodationApplicable(spaceType)
                && MealPricingPolicy.usesSeparateMealBilling(space)) {
            return DashboardFinancialSource.HYBRID;
        }
        if (isAccommodationApplicable(spaceType)) {
            return DashboardFinancialSource.OCCUPANCY;
        }
        return DashboardFinancialSource.API;
    }

    private List<OccupancyEntity> loadBillableOccupancies(UUID spaceId, YearMonth month) {
        LocalDateTime monthStartTime = month.atDay(1).atStartOfDay();
        LocalDateTime monthEndExclusive = month.plusMonths(1).atDay(1).atStartOfDay();
        return occupancyRepository.findBillableBySpaceIdForMonth(
                        spaceId, monthStartTime, monthEndExclusive, month.atEndOfMonth())
                .stream()
                .filter(occupancy -> OccupancyBillingCalculator.isBillableInMonth(occupancy, month))
                .toList();
    }

    private Set<UUID> collectRelevantMemberIds(
            SpaceType spaceType,
            List<MealParticipationEntity> participations,
            List<OccupancyEntity> activeOccupancies) {
        Set<UUID> memberIds = new HashSet<>();
        if (spaceType == SpaceType.MESS || !participations.isEmpty()) {
            participations.forEach(participation -> memberIds.add(participation.getMember().getId()));
        }
        if (isAccommodationApplicable(spaceType)) {
            activeOccupancies.forEach(occupancy -> memberIds.add(occupancy.getMember().getId()));
        }
        return memberIds;
    }

    private Map<UUID, MemberEntity> loadMembers(UUID spaceId, Set<UUID> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, MemberEntity> membersById = new HashMap<>();
        for (MemberEntity member : memberRepository.findBySpaceIdAndActiveTrue(spaceId)) {
            if (memberIds.contains(member.getId())) {
                membersById.put(member.getId(), member);
            }
        }
        return membersById;
    }

    private Map<UUID, OccupancyEntity> mapOccupancyByMember(List<OccupancyEntity> occupancies) {
        Map<UUID, OccupancyEntity> byMember = new HashMap<>();
        for (OccupancyEntity occupancy : occupancies) {
            byMember.put(occupancy.getMember().getId(), occupancy);
        }
        return byMember;
    }

    private BigDecimal sumNullable(List<BigDecimal> values) {
        BigDecimal total = BigDecimal.ZERO;
        boolean hasValue = false;
        for (BigDecimal value : values) {
            if (value == null) {
                continue;
            }
            total = total.add(value);
            hasValue = true;
        }
        return hasValue ? total : null;
    }

    private boolean isAccommodationApplicable(SpaceType spaceType) {
        return spaceType != SpaceType.MESS;
    }

    private SpaceEntity loadSpace(UUID spaceId) {
        return spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));
    }

    private YearMonth parseMonth(String monthParam) {
        if (monthParam == null || monthParam.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(monthParam);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("Invalid month format. Expected YYYY-MM", HttpStatus.BAD_REQUEST);
        }
    }
}
