package com.amico.amico_backend.dashboard.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.amico.amico_backend.dashboard.api.dto.response.PrepaidBalanceSummaryResponse;
import com.amico.amico_backend.meal.application.service.MemberMealBalanceService;
import com.amico.amico_backend.space.domain.model.MealBillingType;
import com.amico.amico_backend.space.domain.model.PrepaidBalanceUnit;
import com.amico.amico_backend.space.domain.model.SpaceType;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.amico.amico_backend.user.infrastructure.persistence.entity.UserEntity;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrepaidBalanceBillingCalculatorTest {

    @Mock
    private MemberMealBalanceService memberMealBalanceService;

    @InjectMocks
    private PrepaidBalanceBillingCalculator calculator;

    @Test
    void aggregateSpaceSummary_usesWalletAggregates() {
        UUID spaceId = UUID.randomUUID();
        SpaceEntity space = SpaceEntity.builder()
                .owner(UserEntity.builder().build())
                .name("Test Mess")
                .type(SpaceType.MESS)
                .mealBillingType(MealBillingType.PREPAID_BALANCE)
                .prepaidBalanceUnit(PrepaidBalanceUnit.MEALS)
                .prepaidFallbackToPayPerMeal(true)
                .build();
        space.setId(spaceId);

        when(memberMealBalanceService.sumPurchasedForSpace(eq(spaceId), any(YearMonth.class)))
                .thenReturn(new BigDecimal("30"));
        when(memberMealBalanceService.sumConsumedForSpace(eq(spaceId), any(YearMonth.class)))
                .thenReturn(new BigDecimal("12"));
        when(memberMealBalanceService.sumRemainingForSpace(spaceId)).thenReturn(new BigDecimal("18"));
        when(memberMealBalanceService.sumPaidForSpace(eq(spaceId), any(YearMonth.class)))
                .thenReturn(new BigDecimal("9000"));

        PrepaidBalanceSummaryResponse summary =
                calculator.aggregateSpaceSummary(space, YearMonth.of(2026, 6));

        assertThat(summary.getBalanceSold()).isEqualByComparingTo("30");
        assertThat(summary.getBalanceConsumed()).isEqualByComparingTo("12");
        assertThat(summary.getBalanceRemaining()).isEqualByComparingTo("18");
        assertThat(summary.getAmountCollected()).isEqualByComparingTo("9000");
        assertThat(summary.getUnit()).isEqualTo(PrepaidBalanceUnit.MEALS);
    }
}
