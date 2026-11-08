package com.acomi.acomi_backend.dashboard.application.support;

import com.acomi.acomi_backend.occupancy.domain.model.OccupancyPricingBasis;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.domain.model.StayKind;
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
        if (occupancy.getStayKind() == StayKind.SHORT) {
            return null;
        }

        if (month == null) {
            BigDecimal contract = computeContractMonthlyTotal(occupancy, null);
            if (contract == null) {
                return null;
            }
            BillingPeriod period = BillingAmountCalculator.fullMonthPeriod(YearMonth.now());
            return BillingAmountCalculator.calculate(contract, period, tax, billingDueDay);
        }

        if (!isBillableInMonth(occupancy, month)) {
            return null;
        }

        BillingPeriod period = resolveOccupancyPeriod(occupancy, month);
        if (period == null) {
            return null;
        }

        // Mid-month OPEN_ENDED daily-rate revision: prior rate before effective date, new rate from it.
        BigDecimal splitDaily = computeOpenEndedDailySplitBase(occupancy, period);
        if (splitDaily != null) {
            return BillingAmountCalculator.calculateFixedPeriodAmount(splitDaily, period, tax, billingDueDay);
        }

        BigDecimal contract = computeContractMonthlyTotal(occupancy, month);
        if (contract == null) {
            return null;
        }

        return BillingAmountCalculator.calculate(contract, period, tax, billingDueDay);
    }

    /**
     * When {@code rentEffectiveMonth} falls inside the billable window of a DAILY_RATE
     * OPEN_ENDED stay, charge prior×daysBefore + new×daysFrom. Returns null when no split applies.
     */
    static BigDecimal computeOpenEndedDailySplitBase(OccupancyEntity occupancy, BillingPeriod period) {
        if (occupancy == null
                || period == null
                || !isOpenEndedDailyRate(occupancy)
                || occupancy.getRentEffectiveMonth() == null
                || occupancy.getPriorRentSnapshot() == null
                || occupancy.getRentSnapshot() == null) {
            return null;
        }
        LocalDate effective = occupancy.getRentEffectiveMonth();
        LocalDate periodStart = period.getStart();
        LocalDate periodEnd = period.getEnd();
        if (effective.isAfter(periodEnd) || !effective.isAfter(periodStart)) {
            // Entire period before effective → prior handled by rentAmountForMonth;
            // effective on/before period start → new rate only (no split).
            if (effective.isAfter(periodEnd)) {
                // whole period prior to effective — let standard path use prior via month compare
                return null;
            }
            return null;
        }
        // Days [periodStart, effective) at prior; [effective, periodEnd] at new (inclusive ends).
        int priorDays = (int) java.time.temporal.ChronoUnit.DAYS.between(periodStart, effective);
        int newDays = (int) java.time.temporal.ChronoUnit.DAYS.between(effective, periodEnd) + 1;
        if (priorDays < 0 || newDays < 0 || priorDays + newDays != period.getBillableDays()) {
            return null;
        }
        BigDecimal priorPart = occupancy.getPriorRentSnapshot().multiply(BigDecimal.valueOf(priorDays));
        BigDecimal newPart = occupancy.getRentSnapshot().multiply(BigDecimal.valueOf(newDays));
        return BillingAmountCalculator.roundMoney(priorPart.add(newPart));
    }

    public static boolean isBillableInMonth(OccupancyEntity occupancy, YearMonth month) {
        if (occupancy.getStayKind() == StayKind.SHORT) {
            return false;
        }

        if (occupancy.getStatus() == OccupancyStatus.RESERVED) {
            return false;
        }

        // A cancelled reservation is closed as VACATED and never receives actualMoveInAt.
        // A real checkout has that timestamp, including legacy rows the tests now set.
        if (occupancy.getStatus() == OccupancyStatus.VACATED && occupancy.getActualMoveInAt() == null) {
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

    /**
     * OPEN_ENDED with {@code DAILY_RATE}: payable RENT rows are generated only after the
     * billing period closes (calendar month ended, or occupancy vacated). MONTHLY and
     * legacy OPEN_ENDED (null pricing basis) keep generate-on-activation / open-month sync.
     */
    public static boolean isOpenEndedDailyRate(OccupancyEntity occupancy) {
        return occupancy != null
                && occupancy.getStayKind() == StayKind.OPEN_ENDED
                && OccupancyPricingBasis.isDailyRate(occupancy.getPricingBasis());
    }

    public static boolean shouldGeneratePayableRent(OccupancyEntity occupancy, YearMonth month) {
        return shouldGeneratePayableRent(occupancy, month, LocalDate.now());
    }

    public static boolean shouldGeneratePayableRent(
            OccupancyEntity occupancy, YearMonth month, LocalDate asOfDate) {
        if (occupancy == null || month == null || asOfDate == null) {
            return false;
        }
        if (!isBillableInMonth(occupancy, month)) {
            return false;
        }
        if (!isOpenEndedDailyRate(occupancy)) {
            return true;
        }
        YearMonth asOfMonth = YearMonth.from(asOfDate);
        // Closed calendar month (period ended).
        if (month.isBefore(asOfMonth)) {
            return true;
        }
        // Checkout closes the member's period immediately — generate through vacate date.
        LocalDate vacated = resolveOccupancyEndDate(occupancy);
        if (occupancy.getStatus() == OccupancyStatus.VACATED
                && vacated != null
                && !vacated.isAfter(asOfDate)
                && !YearMonth.from(vacated).isBefore(month)
                && !YearMonth.from(vacated).isAfter(month)) {
            return true;
        }
        // Current / future open month while still active — accrue only, no payable row.
        return false;
    }

    /**
     * Whether occupancy contract rent should enter Expected / financial KPIs for {@code month}.
     *
     * <p>OPEN_ENDED {@code DAILY_RATE} accrues during an open month but must not inflate Expected
     * until a payable obligation exists (same gate as {@link #shouldGeneratePayableRent}).
     * Otherwise Expected can exceed Pending with no matching payment rows.
     */
    public static boolean contributesToExpectedCharges(OccupancyEntity occupancy, YearMonth month) {
        return contributesToExpectedCharges(occupancy, month, LocalDate.now());
    }

    public static boolean contributesToExpectedCharges(
            OccupancyEntity occupancy, YearMonth month, LocalDate asOfDate) {
        if (occupancy == null || month == null || asOfDate == null) {
            return false;
        }
        if (!isBillableInMonth(occupancy, month)) {
            return false;
        }
        if (!isOpenEndedDailyRate(occupancy)) {
            return true;
        }
        return shouldGeneratePayableRent(occupancy, month, asOfDate);
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

    private static BigDecimal computeContractMonthlyTotal(OccupancyEntity occupancy, YearMonth month) {
        BigDecimal rent = rentAmountForMonth(occupancy, month);
        if (rent == null) {
            return null;
        }

        // Daily-rate Until I Leave: rent_snapshot is ₹/day. No separate monthly food line —
        // food is either included in the daily rate or unsupported as a monthly add-on.
        if (OccupancyPricingBasis.isDailyRate(occupancy.getPricingBasis())) {
            return rent;
        }

        if (occupancy.isFoodIncludedInRent()) {
            return rent;
        }

        if (!occupancy.isFoodEnabled()) {
            return rent;
        }

        BigDecimal food = occupancy.getFoodChargeSnapshot() != null
                ? occupancy.getFoodChargeSnapshot()
                : BigDecimal.ZERO;
        return rent.add(food);
    }

    /**
     * A rent change applies from {@code rentEffectiveMonth}. Earlier months keep
     * {@code priorRentSnapshot} so an existing pending or rejected row is not
     * recalculated at the new price.
     *
     * <p>When {@code pricing_basis = DAILY_RATE}, {@code rent_snapshot} is the daily rate.
     * The calendar-month contract amount is {@code dailyRate × daysInMonth}; existing
     * proration then yields {@code dailyRate × billableDays} for partial months — one
     * {@code RENT/MONTHLY} payment per month, not one payment per day.
     */
    private static BigDecimal rentAmountForMonth(OccupancyEntity occupancy, YearMonth month) {
        LocalDate effective = occupancy.getRentEffectiveMonth();
        BigDecimal snapshot;
        // Months strictly before the effective date keep the prior rate.
        if (effective != null && month != null && YearMonth.from(effective).isAfter(month)) {
            snapshot = occupancy.getPriorRentSnapshot() != null
                    ? occupancy.getPriorRentSnapshot()
                    : occupancy.getRentSnapshot();
        } else {
            snapshot = occupancy.getRentSnapshot();
        }
        if (snapshot == null) {
            return null;
        }
        if (OccupancyPricingBasis.isDailyRate(occupancy.getPricingBasis())) {
            int daysInMonth = month != null ? month.lengthOfMonth() : YearMonth.now().lengthOfMonth();
            return snapshot.multiply(BigDecimal.valueOf(daysInMonth));
        }
        return snapshot;
    }
}
