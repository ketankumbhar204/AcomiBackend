package com.acomi.acomi_backend.dashboard.application.support;

import com.acomi.acomi_backend.dashboard.api.dto.response.PrepaidBalanceSummaryResponse;
import com.acomi.acomi_backend.meal.application.service.MemberMealBalanceService;
import com.acomi.acomi_backend.space.domain.model.MealBillingType;
import com.acomi.acomi_backend.space.domain.model.PrepaidBalanceUnit;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PrepaidBalanceBillingCalculator {

    private static final String DEFAULT_CURRENCY = "INR";

    private final MemberMealBalanceService memberMealBalanceService;

    public MealLedgerContribution computeMemberContribution(
            SpaceEntity space,
            UUID spaceId,
            UUID memberId,
            UUID callerId,
            YearMonth month,
            PayPerMealBillingCalculator payPerMealBillingCalculator) {
        if (space.isPrepaidFallbackToPayPerMeal()) {
            return payPerMealBillingCalculator.computeMemberContribution(spaceId, memberId, callerId, month);
        }

        BigDecimal collected = memberMealBalanceService.sumPaid(spaceId, memberId, month);
        boolean hasCollected = collected.compareTo(BigDecimal.ZERO) > 0;
        return MealLedgerContribution.builder()
                .collected(hasCollected ? collected : null)
                .hasCollected(hasCollected)
                .currencyCode(DEFAULT_CURRENCY)
                .build();
    }

    public PrepaidBalanceSummaryResponse aggregateSpaceSummary(SpaceEntity space, YearMonth month) {
        PrepaidBalanceUnit unit = space.getPrepaidBalanceUnit() != null
                ? space.getPrepaidBalanceUnit()
                : PrepaidBalanceUnit.MEALS;

        BigDecimal sold = memberMealBalanceService.sumPurchasedForSpace(space.getId(), month);
        BigDecimal consumed = memberMealBalanceService.sumConsumedForSpace(space.getId(), month);
        BigDecimal remaining = memberMealBalanceService.sumRemainingForSpace(space.getId());
        BigDecimal amountCollected = memberMealBalanceService.sumPaidForSpace(space.getId(), month);

        return PrepaidBalanceSummaryResponse.builder()
                .balanceSold(sold)
                .balanceConsumed(consumed)
                .balanceRemaining(remaining)
                .amountCollected(amountCollected.compareTo(BigDecimal.ZERO) > 0 ? amountCollected : null)
                .unit(unit)
                .currencyCode(DEFAULT_CURRENCY)
                .build();
    }

    public MemberMonthlyBalance memberMonthlyBalance(UUID spaceId, UUID memberId, YearMonth month) {
        return new MemberMonthlyBalance(
                memberMealBalanceService.findBalance(spaceId, memberId),
                memberMealBalanceService.sumPurchased(spaceId, memberId, month),
                memberMealBalanceService.sumConsumed(spaceId, memberId, month));
    }

    public record MemberMonthlyBalance(
            com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MemberMealBalanceEntity balance,
            BigDecimal purchased,
            BigDecimal consumed) {

        public BigDecimal remaining() {
            return balance != null ? balance.getBalance() : BigDecimal.ZERO;
        }
    }

    public boolean supports(MealBillingType billingType) {
        return billingType == MealBillingType.PREPAID_BALANCE;
    }
}
