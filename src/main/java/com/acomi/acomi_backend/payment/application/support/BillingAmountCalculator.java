package com.acomi.acomi_backend.payment.application.support;

import com.acomi.acomi_backend.space.domain.model.PriceTaxMode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import lombok.Builder;
import lombok.Getter;

/**
 * Centralized calendar-month proration + tax calculation.
 * Money: {@link BigDecimal}, scale 2, {@link RoundingMode#HALF_UP}.
 */
public final class BillingAmountCalculator {

    public static final int MONEY_SCALE = 2;
    public static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal HUNDRED = new BigDecimal("100");

    private BillingAmountCalculator() {}

    @Getter
    @Builder
    public static final class BillingPeriod {
        private final LocalDate start;
        private final LocalDate end;
        private final int billableDays;
        private final int daysInMonth;
        private final boolean prorated;
        private final YearMonth month;
    }

    @Getter
    @Builder
    public static final class TaxInput {
        private final boolean taxEnabled;
        private final BigDecimal taxRatePercent;
        private final PriceTaxMode priceTaxMode;

        public static TaxInput none() {
            return TaxInput.builder().taxEnabled(false).build();
        }
    }

    @Getter
    @Builder
    public static final class BillingAmountResult {
        private final BillingPeriod period;
        private final BigDecimal configuredMonthlyAmount;
        private final BigDecimal baseAmount;
        private final BigDecimal taxAmount;
        private final BigDecimal totalAmount;
        private final boolean taxEnabled;
        private final BigDecimal taxRatePercent;
        private final PriceTaxMode priceTaxMode;
        private final LocalDate dueDate;
    }

    /**
     * Inclusive occupancy window clipped to the calendar month.
     * Returns null if there is no overlap.
     */
    public static BillingPeriod resolvePeriod(
            YearMonth month, LocalDate occupancyStart, LocalDate occupancyEndInclusive) {
        Objects.requireNonNull(month, "month");
        Objects.requireNonNull(occupancyStart, "occupancyStart");

        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();

        if (occupancyStart.isAfter(monthEnd)) {
            return null;
        }
        if (occupancyEndInclusive != null && occupancyEndInclusive.isBefore(monthStart)) {
            return null;
        }

        LocalDate periodStart = occupancyStart.isAfter(monthStart) ? occupancyStart : monthStart;
        LocalDate periodEnd = occupancyEndInclusive == null || occupancyEndInclusive.isAfter(monthEnd)
                ? monthEnd
                : occupancyEndInclusive;

        if (periodStart.isAfter(periodEnd)) {
            return null;
        }

        int billableDays = (int) ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
        int daysInMonth = month.lengthOfMonth();
        boolean prorated = billableDays < daysInMonth;

        return BillingPeriod.builder()
                .start(periodStart)
                .end(periodEnd)
                .billableDays(billableDays)
                .daysInMonth(daysInMonth)
                .prorated(prorated)
                .month(month)
                .build();
    }

    /** Full calendar month period (no occupancy clip). */
    public static BillingPeriod fullMonthPeriod(YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        int days = month.lengthOfMonth();
        return BillingPeriod.builder()
                .start(start)
                .end(end)
                .billableDays(days)
                .daysInMonth(days)
                .prorated(false)
                .month(month)
                .build();
    }

    public static BigDecimal prorateConfigured(BigDecimal monthlyConfigured, BillingPeriod period) {
        if (monthlyConfigured == null || period == null) {
            return null;
        }
        if (period.getBillableDays() <= 0 || period.getDaysInMonth() <= 0) {
            return roundMoney(BigDecimal.ZERO);
        }
        if (!period.isProrated()) {
            return roundMoney(monthlyConfigured);
        }
        return roundMoney(monthlyConfigured
                .multiply(BigDecimal.valueOf(period.getBillableDays()))
                .divide(BigDecimal.valueOf(period.getDaysInMonth()), 8, MONEY_ROUNDING));
    }

    public static BillingAmountResult calculate(
            BigDecimal monthlyConfigured,
            BillingPeriod period,
            TaxInput tax,
            int billingDueDay) {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(tax, "tax");

        BigDecimal configured = monthlyConfigured != null ? monthlyConfigured : BigDecimal.ZERO;
        BigDecimal proratedConfigured = prorateConfigured(configured, period);

        AmountBreakdown breakdown = applyTax(proratedConfigured, tax);
        LocalDate dueDate = resolveDueDate(period, billingDueDay);

        return BillingAmountResult.builder()
                .period(period)
                .configuredMonthlyAmount(roundMoney(configured))
                .baseAmount(breakdown.base)
                .taxAmount(breakdown.tax)
                .totalAmount(breakdown.total)
                .taxEnabled(tax.isTaxEnabled() && tax.getTaxRatePercent() != null)
                .taxRatePercent(tax.isTaxEnabled() ? tax.getTaxRatePercent() : null)
                .priceTaxMode(tax.isTaxEnabled() ? tax.getPriceTaxMode() : null)
                .dueDate(dueDate)
                .build();
    }

    /**
     * Apply tax to an already-finalized period amount (e.g. meal activity sum for a month).
     * Period metadata is attached for snapshots; amount is not calendar-prorated again.
     */
    public static BillingAmountResult calculateFixedPeriodAmount(
            BigDecimal periodConfiguredAmount,
            BillingPeriod period,
            TaxInput tax,
            int billingDueDay) {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(tax, "tax");

        BigDecimal configured =
                periodConfiguredAmount != null ? roundMoney(periodConfiguredAmount) : BigDecimal.ZERO;
        AmountBreakdown breakdown = applyTax(configured, tax);
        LocalDate dueDate = resolveDueDate(period, billingDueDay);

        return BillingAmountResult.builder()
                .period(period)
                .configuredMonthlyAmount(configured)
                .baseAmount(breakdown.base)
                .taxAmount(breakdown.tax)
                .totalAmount(breakdown.total)
                .taxEnabled(tax.isTaxEnabled() && tax.getTaxRatePercent() != null)
                .taxRatePercent(tax.isTaxEnabled() ? tax.getTaxRatePercent() : null)
                .priceTaxMode(tax.isTaxEnabled() ? tax.getPriceTaxMode() : null)
                .dueDate(dueDate)
                .build();
    }

    public static LocalDate resolveDueDate(BillingPeriod period, int billingDueDay) {
        int dueDay = Math.min(Math.max(billingDueDay, 1), 28);
        if (period.isProrated() && period.getStart().getDayOfMonth() > 1) {
            // First/final partial: due on period start (tenant not liable before join / after leave window).
            return period.getStart();
        }
        return period.getMonth().atDay(Math.min(dueDay, period.getMonth().lengthOfMonth()));
    }

    public static BigDecimal roundMoney(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private static AmountBreakdown applyTax(BigDecimal configuredAfterProration, TaxInput tax) {
        BigDecimal amount = configuredAfterProration != null
                ? roundMoney(configuredAfterProration)
                : BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);

        if (!tax.isTaxEnabled()
                || tax.getTaxRatePercent() == null
                || tax.getTaxRatePercent().compareTo(BigDecimal.ZERO) == 0
                || tax.getPriceTaxMode() == null) {
            return new AmountBreakdown(amount, BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING), amount);
        }

        BigDecimal rate = tax.getTaxRatePercent();
        if (tax.getPriceTaxMode() == PriceTaxMode.EXCLUSIVE) {
            BigDecimal base = amount;
            BigDecimal taxAmount = roundMoney(base.multiply(rate).divide(HUNDRED, 8, MONEY_ROUNDING));
            BigDecimal total = roundMoney(base.add(taxAmount));
            return new AmountBreakdown(base, taxAmount, total);
        }

        // INCLUSIVE: configured amount is final payable.
        BigDecimal total = amount;
        BigDecimal divisor = BigDecimal.ONE.add(rate.divide(HUNDRED, 8, MONEY_ROUNDING));
        BigDecimal base = roundMoney(total.divide(divisor, 8, MONEY_ROUNDING));
        BigDecimal taxAmount = roundMoney(total.subtract(base));
        // Ensure base + tax = total exactly after rounding.
        if (base.add(taxAmount).compareTo(total) != 0) {
            taxAmount = total.subtract(base);
        }
        return new AmountBreakdown(base, taxAmount, total);
    }

    private record AmountBreakdown(BigDecimal base, BigDecimal tax, BigDecimal total) {}
}
