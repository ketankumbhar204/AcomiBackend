package com.acomi.acomi_backend.payment.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.payment.application.support.BillingAmountCalculator.BillingAmountResult;
import com.acomi.acomi_backend.payment.application.support.BillingAmountCalculator.BillingPeriod;
import com.acomi.acomi_backend.payment.application.support.BillingAmountCalculator.TaxInput;
import com.acomi.acomi_backend.space.domain.model.PriceTaxMode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class BillingAmountCalculatorTest {

    @Test
    void proratesMidMonthJoinInclusive_september() {
        BillingPeriod period = BillingAmountCalculator.resolvePeriod(
                YearMonth.of(2026, 9), LocalDate.of(2026, 9, 15), null);

        assertThat(period.getBillableDays()).isEqualTo(16);
        assertThat(period.getDaysInMonth()).isEqualTo(30);
        assertThat(period.isProrated()).isTrue();
        assertThat(period.getStart()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(period.getEnd()).isEqualTo(LocalDate.of(2026, 9, 30));

        BillingAmountResult result = BillingAmountCalculator.calculate(
                new BigDecimal("10000"), period, TaxInput.none(), 1);

        assertThat(result.getTotalAmount()).isEqualByComparingTo("5333.33");
        assertThat(result.getBaseAmount()).isEqualByComparingTo("5333.33");
        assertThat(result.getTaxAmount()).isEqualByComparingTo("0.00");
        assertThat(result.getDueDate()).isEqualTo(LocalDate.of(2026, 9, 15));
    }

    @Test
    void fullMonthJoinOnFirst_dueOnFirst() {
        BillingPeriod period = BillingAmountCalculator.resolvePeriod(
                YearMonth.of(2026, 10), LocalDate.of(2026, 10, 1), null);

        assertThat(period.isProrated()).isFalse();
        BillingAmountResult result = BillingAmountCalculator.calculate(
                new BigDecimal("10000"), period, TaxInput.none(), 1);

        assertThat(result.getTotalAmount()).isEqualByComparingTo("10000.00");
        assertThat(result.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void joinOnLastDay_oneDay() {
        BillingPeriod period = BillingAmountCalculator.resolvePeriod(
                YearMonth.of(2026, 9), LocalDate.of(2026, 9, 30), null);

        assertThat(period.getBillableDays()).isEqualTo(1);
        BillingAmountResult result = BillingAmountCalculator.calculate(
                new BigDecimal("10000"), period, TaxInput.none(), 1);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("333.33");
    }

    @Test
    void februaryLeapYear() {
        BillingPeriod period = BillingAmountCalculator.resolvePeriod(
                YearMonth.of(2024, 2), LocalDate.of(2024, 2, 15), null);

        assertThat(period.getDaysInMonth()).isEqualTo(29);
        assertThat(period.getBillableDays()).isEqualTo(15);
    }

    @Test
    void februaryNonLeap() {
        BillingPeriod period = BillingAmountCalculator.resolvePeriod(
                YearMonth.of(2026, 2), LocalDate.of(2026, 2, 15), null);

        assertThat(period.getDaysInMonth()).isEqualTo(28);
        assertThat(period.getBillableDays()).isEqualTo(14);
    }

    @Test
    void vacateMidMonth_inclusiveEnd() {
        BillingPeriod period = BillingAmountCalculator.resolvePeriod(
                YearMonth.of(2026, 10),
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 15));

        assertThat(period.getBillableDays()).isEqualTo(15);
        assertThat(period.getEnd()).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    void taxExclusiveOnProrated() {
        BillingPeriod period = BillingAmountCalculator.resolvePeriod(
                YearMonth.of(2026, 9), LocalDate.of(2026, 9, 15), null);
        TaxInput tax = TaxInput.builder()
                .taxEnabled(true)
                .taxRatePercent(new BigDecimal("18"))
                .priceTaxMode(PriceTaxMode.EXCLUSIVE)
                .build();

        BillingAmountResult result =
                BillingAmountCalculator.calculate(new BigDecimal("10000"), period, tax, 1);

        assertThat(result.getBaseAmount()).isEqualByComparingTo("5333.33");
        assertThat(result.getTaxAmount()).isEqualByComparingTo("960.00");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("6293.33");
    }

    @Test
    void taxInclusiveOnProrated() {
        BillingPeriod period = BillingAmountCalculator.resolvePeriod(
                YearMonth.of(2026, 9), LocalDate.of(2026, 9, 15), null);
        TaxInput tax = TaxInput.builder()
                .taxEnabled(true)
                .taxRatePercent(new BigDecimal("18"))
                .priceTaxMode(PriceTaxMode.INCLUSIVE)
                .build();

        BillingAmountResult result =
                BillingAmountCalculator.calculate(new BigDecimal("10000"), period, tax, 1);

        assertThat(result.getTotalAmount()).isEqualByComparingTo("5333.33");
        assertThat(result.getBaseAmount().add(result.getTaxAmount()))
                .isEqualByComparingTo(result.getTotalAmount());
        assertThat(result.getBaseAmount()).isEqualByComparingTo("4519.77");
        assertThat(result.getTaxAmount()).isEqualByComparingTo("813.56");
    }

    @Test
    void taxInclusiveFullMonth_totalUnchanged() {
        BillingPeriod period = BillingAmountCalculator.fullMonthPeriod(YearMonth.of(2026, 10));
        TaxInput tax = TaxInput.builder()
                .taxEnabled(true)
                .taxRatePercent(new BigDecimal("18"))
                .priceTaxMode(PriceTaxMode.INCLUSIVE)
                .build();

        BillingAmountResult result =
                BillingAmountCalculator.calculate(new BigDecimal("10000"), period, tax, 1);

        assertThat(result.getTotalAmount()).isEqualByComparingTo("10000.00");
        assertThat(result.getBaseAmount().add(result.getTaxAmount()))
                .isEqualByComparingTo("10000.00");
    }

    @Test
    void fixedPeriodMealAmount_appliesTaxWithoutReProration() {
        BillingPeriod period = BillingAmountCalculator.fullMonthPeriod(YearMonth.of(2026, 9));
        TaxInput tax = TaxInput.builder()
                .taxEnabled(true)
                .taxRatePercent(new BigDecimal("18"))
                .priceTaxMode(PriceTaxMode.EXCLUSIVE)
                .build();

        BillingAmountResult result = BillingAmountCalculator.calculateFixedPeriodAmount(
                new BigDecimal("2500"), period, tax, 1);

        assertThat(result.getBaseAmount()).isEqualByComparingTo("2500.00");
        assertThat(result.getTaxAmount()).isEqualByComparingTo("450.00");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("2950.00");
        assertThat(result.getDueDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    }
}
