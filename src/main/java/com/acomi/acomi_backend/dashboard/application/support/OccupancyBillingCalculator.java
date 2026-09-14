package com.acomi.acomi_backend.dashboard.application.support;

import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.payment.application.support.BillingAmountCalculator;
import com.acomi.acomi_backend.payment.application.support.BillingAmountCalculator.BillingAmountResult;
import com.acomi.acomi_backend.payment.application.support.BillingAmountCalculator.BillingPeriod;
import com.acomi.acomi_backend.payment.application.support.BillingAmountCalculator.TaxInput;
import com.acomi.acomi_backend.space.domain.model.PriceTaxMode;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

public final class OccupancyBillingCalculator {

    private OccupancyBillingCalculator() {}

    public static BigDecimal computeMonthlyExpected(OccupancyEntity occupancy) {
        return computeMonthlyExpected(occupancy, null);
    }

    /**
     * Contract monthly total for the month after proration (no tax).
     * Prefer {@link #computeBilling(OccupancyEntity, YearMonth, SpaceEntity)} when tax/due date needed.
     */
    public static BigDecimal computeMonthlyExpected(OccupancyEntity occupancy, YearMonth month) {
        BillingAmountResult result = computeBilling(occupancy, month, TaxInput.none(), 1);
        return result != null ? result.getTotalAmount() : null;
    }

    public static BillingAmountResult computeBilling(
            OccupancyEntity occupancy, YearMonth month, SpaceEntity space) {
        TaxInput tax = resolveTaxInput(space);
        int dueDay = space != null && space.getBillingDueDay() > 0 ? space.getBillingDueDay() : 1;
        return computeBilling(occupancy, month, tax, dueDay);
    }

    public static BillingAmountResult computeBilling(
            OccupancyEntity occupancy, YearMonth month, TaxInput tax, int billingDueDay) {
        if (month == null) {
            BigDecimal contract = computeContractMonthlyTotal(occupancy);
            if (contract == null) {
                return null;
            }
            BillingPeriod period = BillingAmountCalculator.fullMonthPeriod(YearMonth.now());
            return BillingAmountCalculator.calculate(contract, period, tax, billingDueDay);
        }

        if (!isBillableInMonth(occupancy, month)) {
            return null;
        }

        BigDecimal contract = computeContractMonthlyTotal(occupancy);
        if (contract == null) {
            return null;
        }

        BillingPeriod period = resolveOccupancyPeriod(occupancy, month);
        if (period == null) {
            return null;
        }

        return BillingAmountCalculator.calculate(contract, period, tax, billingDueDay);
    }

    public static boolean isBillableInMonth(OccupancyEntity occupancy, YearMonth month) {
        if (occupancy.getStatus() == OccupancyStatus.RESERVED) {
            return false;
        }

        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        LocalDate occupancyStart = resolveOccupancyStartDate(occupancy);

        if (occupancyStart.isAfter(monthEnd)) {
            return false;
        }

        LocalDate occupancyEnd = resolveOccupancyEndDate(occupancy);
        if (occupancyEnd != null && occupancyEnd.isBefore(monthStart)) {
            return false;
        }

        return occupancy.getStatus() == OccupancyStatus.ACTIVE
                || occupancy.getStatus() == OccupancyStatus.VACATED;
    }

    public static LocalDate resolveOccupancyStartDate(OccupancyEntity occupancy) {
        LocalDateTime actualMoveInAt = occupancy.getActualMoveInAt();
        if (actualMoveInAt != null) {
            return actualMoveInAt.toLocalDate();
        }
        return occupancy.getMoveInDate();
    }

    public static LocalDate resolveOccupancyEndDate(OccupancyEntity occupancy) {
        LocalDateTime vacatedAt = occupancy.getVacatedAt();
        return vacatedAt != null ? vacatedAt.toLocalDate() : null;
    }

    public static BillingPeriod resolveOccupancyPeriod(OccupancyEntity occupancy, YearMonth month) {
        return BillingAmountCalculator.resolvePeriod(
                month, resolveOccupancyStartDate(occupancy), resolveOccupancyEndDate(occupancy));
    }

    public static TaxInput resolveTaxInput(SpaceEntity space) {
        if (space == null || !space.isTaxEnabled()) {
            return TaxInput.none();
        }
        PriceTaxMode mode = space.getPriceTaxMode() != null ? space.getPriceTaxMode() : PriceTaxMode.EXCLUSIVE;
        return TaxInput.builder()
                .taxEnabled(true)
                .taxRatePercent(space.getTaxRatePercent())
                .priceTaxMode(mode)
                .build();
    }

    private static BigDecimal computeContractMonthlyTotal(OccupancyEntity occupancy) {
        if (occupancy.getRentSnapshot() == null) {
            return null;
        }

        if (occupancy.isFoodIncludedInRent()) {
            return occupancy.getRentSnapshot();
        }

        if (!occupancy.isFoodEnabled()) {
            return occupancy.getRentSnapshot();
        }

        BigDecimal food = occupancy.getFoodChargeSnapshot() != null
                ? occupancy.getFoodChargeSnapshot()
                : BigDecimal.ZERO;
        return occupancy.getRentSnapshot().add(food);
    }
}
