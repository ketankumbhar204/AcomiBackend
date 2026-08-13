package com.acomi.acomi_backend.dashboard.application.support;

import com.acomi.acomi_backend.meal.api.dto.response.MemberMealActivitySummaryResponse;
import com.acomi.acomi_backend.meal.application.service.MemberMealActivityService;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PayPerMealBillingCalculator {

    private static final String DEFAULT_CURRENCY = "INR";

    private final MemberMealActivityService memberMealActivityService;

    /**
     * Request-scoped reuse so sync + ledger in the same owner-month load do not
     * recompute meal activity N× each.
     */
    private final ThreadLocal<Map<String, MealLedgerContribution>> requestCache = new ThreadLocal<>();

    public void beginRequestCache() {
        requestCache.set(new HashMap<>());
    }

    public void endRequestCache() {
        requestCache.remove();
    }

    public MealLedgerContribution computeMemberContribution(
            UUID spaceId, UUID memberId, UUID callerId, YearMonth month) {
        String cacheKey = spaceId + "|" + memberId + "|" + month;
        Map<String, MealLedgerContribution> cache = requestCache.get();
        if (cache != null && cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }

        MemberMealActivitySummaryResponse mealSummary =
                memberMealActivityService.getMonthlyActivity(spaceId, memberId, callerId, month.toString())
                        .getSummary();

        BigDecimal expected = mealSummary.getAmountGenerated();
        BigDecimal collected = mealSummary.getPaidAmount();
        String currencyCode = mealSummary.getCurrencyCode() != null
                ? mealSummary.getCurrencyCode()
                : DEFAULT_CURRENCY;

        MealLedgerContribution contribution = MealLedgerContribution.builder()
                .expected(expected)
                .collected(collected)
                .hasExpected(expected != null)
                .hasCollected(collected != null)
                .currencyCode(currencyCode)
                .build();

        if (cache != null) {
            cache.put(cacheKey, contribution);
        }
        return contribution;
    }
}
