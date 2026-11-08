package com.acomi.acomi_backend.dashboard.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.occupancy.domain.model.OccupancyPricingBasis;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.domain.model.StayKind;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.payment.application.support.BillingAmountCalculator.BillingAmountResult;
import com.acomi.acomi_backend.space.domain.model.PriceTaxMode;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class OccupancyBillingCalculatorTest {

    @Test
    void computeMonthlyExpected_returnsNullWhenRentMissing() {
        OccupancyEntity occupancy = OccupancyEntity.builder().build();

        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(occupancy)).isNull();
    }

    @Test
    void computeMonthlyExpected_returnsRentWhenFoodIncludedInRent() {
        OccupancyEntity occupancy = OccupancyEntity.builder()
                .rentSnapshot(new BigDecimal("8000"))
                .foodIncludedInRent(true)
                .foodEnabled(true)
                .foodChargeSnapshot(new BigDecimal("2500"))
                .build();

        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(occupancy))
                .isEqualByComparingTo(new BigDecimal("8000"));
    }

    @Test
    void computeMonthlyExpected_returnsRentWhenFoodDisabled() {
        OccupancyEntity occupancy = OccupancyEntity.builder()
                .rentSnapshot(new BigDecimal("8000"))
                .foodEnabled(false)
                .foodChargeSnapshot(new BigDecimal("2500"))
                .build();

        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(occupancy))
                .isEqualByComparingTo(new BigDecimal("8000"));
    }

    @Test
    void computeMonthlyExpected_addsFoodChargeWhenFoodEnabled() {
        OccupancyEntity occupancy = OccupancyEntity.builder()
                .rentSnapshot(new BigDecimal("8000"))
                .foodEnabled(true)
                .foodIncludedInRent(false)
                .foodChargeSnapshot(new BigDecimal("2500"))
                .build();

        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(occupancy))
                .isEqualByComparingTo(new BigDecimal("10500"));
    }

    @Test
    void computeMonthlyExpected_treatsMissingFoodChargeAsZero() {
        OccupancyEntity occupancy = OccupancyEntity.builder()
                .rentSnapshot(new BigDecimal("8000"))
                .foodEnabled(true)
                .foodIncludedInRent(false)
                .build();

        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(occupancy))
                .isEqualByComparingTo(new BigDecimal("8000"));
    }

    @Test
    void isBillableInMonth_returnsFalseForReservedOccupancy() {
        OccupancyEntity occupancy = OccupancyEntity.builder()
                .status(OccupancyStatus.RESERVED)
                .moveInDate(LocalDate.of(2026, 7, 1))
                .rentSnapshot(new BigDecimal("8000"))
                .build();

        assertThat(OccupancyBillingCalculator.isBillableInMonth(occupancy, YearMonth.of(2026, 7)))
                .isFalse();
        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(occupancy, YearMonth.of(2026, 7)))
                .isNull();
    }

    @Test
    void isBillableInMonth_returnsFalseBeforeMoveInMonth() {
        OccupancyEntity occupancy = activeOccupancy(
                LocalDate.of(2026, 7, 1), null, new BigDecimal("10000"));

        assertThat(OccupancyBillingCalculator.isBillableInMonth(occupancy, YearMonth.of(2026, 5)))
                .isFalse();
        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(occupancy, YearMonth.of(2026, 5)))
                .isNull();
    }

    @Test
    void midMonthJoin_isProrated() {
        OccupancyEntity occupancy = activeOccupancy(
                LocalDate.of(2026, 9, 15), null, new BigDecimal("10000"));

        assertThat(OccupancyBillingCalculator.isBillableInMonth(occupancy, YearMonth.of(2026, 9)))
                .isTrue();
        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(occupancy, YearMonth.of(2026, 9)))
                .isEqualByComparingTo(new BigDecimal("5333.33"));
    }

    @Test
    void joinOnFirst_fullMonth() {
        OccupancyEntity occupancy = activeOccupancy(
                LocalDate.of(2026, 7, 1), null, new BigDecimal("10000"));

        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(occupancy, YearMonth.of(2026, 7)))
                .isEqualByComparingTo(new BigDecimal("10000.00"));
    }

    @Test
    void isBillableInMonth_returnsFalseAfterVacatedMonth() {
        OccupancyEntity occupancy = OccupancyEntity.builder()
                .status(OccupancyStatus.VACATED)
                .moveInDate(LocalDate.of(2026, 3, 1))
                .actualMoveInAt(LocalDateTime.of(2026, 3, 1, 10, 0))
                .vacatedAt(LocalDateTime.of(2026, 4, 30, 10, 0))
                .rentSnapshot(new BigDecimal("9000"))
                .build();

        assertThat(OccupancyBillingCalculator.isBillableInMonth(occupancy, YearMonth.of(2026, 5)))
                .isFalse();
    }

    @Test
    void vacatedMidMonth_proratesFinalMonth() {
        OccupancyEntity occupancy = OccupancyEntity.builder()
                .status(OccupancyStatus.VACATED)
                .moveInDate(LocalDate.of(2026, 3, 1))
                .actualMoveInAt(LocalDateTime.of(2026, 3, 1, 10, 0))
                .vacatedAt(LocalDateTime.of(2026, 5, 15, 10, 0))
                .rentSnapshot(new BigDecimal("9000"))
                .build();

        assertThat(OccupancyBillingCalculator.isBillableInMonth(occupancy, YearMonth.of(2026, 5)))
                .isTrue();
        // May has 31 days; 1–15 inclusive = 15 days → 9000 * 15 / 31
        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(occupancy, YearMonth.of(2026, 5)))
                .isEqualByComparingTo(new BigDecimal("4354.84"));
    }

    @Test
    void cancelledReservationWithoutMoveIn_isNotBillable() {
        OccupancyEntity occupancy = OccupancyEntity.builder()
                .status(OccupancyStatus.VACATED)
                .moveInDate(LocalDate.of(2026, 11, 10))
                .vacatedAt(LocalDateTime.of(2026, 11, 7, 12, 0))
                .rentSnapshot(new BigDecimal("8000"))
                .build();

        assertThat(OccupancyBillingCalculator.isBillableInMonth(occupancy, YearMonth.of(2026, 11)))
                .isFalse();
        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(occupancy, YearMonth.of(2026, 11)))
                .isNull();
    }

    @Test
    void resolveOccupancyStartDate_prefersActualMoveInAt() {
        OccupancyEntity occupancy = OccupancyEntity.builder()
                .moveInDate(LocalDate.of(2026, 7, 1))
                .actualMoveInAt(LocalDateTime.of(2026, 7, 10, 9, 0))
                .build();

        assertThat(OccupancyBillingCalculator.resolveOccupancyStartDate(occupancy))
                .isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(OccupancyBillingCalculator.isBillableInMonth(occupancy, YearMonth.of(2026, 7)))
                .isTrue();
        assertThat(OccupancyBillingCalculator.isBillableInMonth(occupancy, YearMonth.of(2026, 6)))
                .isFalse();
    }

    @Test
    void computeBilling_appliesSpaceTaxExclusive() {
        OccupancyEntity occupancy = activeOccupancy(
                LocalDate.of(2026, 9, 15), null, new BigDecimal("10000"));
        SpaceEntity space = SpaceEntity.builder()
                .taxEnabled(true)
                .taxRatePercent(new BigDecimal("18"))
                .priceTaxMode(PriceTaxMode.EXCLUSIVE)
                .billingDueDay(1)
                .build();

        BillingAmountResult result =
                OccupancyBillingCalculator.computeBilling(occupancy, YearMonth.of(2026, 9), space);

        assertThat(result.getTotalAmount()).isEqualByComparingTo("6293.33");
        assertThat(result.getDueDate()).isEqualTo(LocalDate.of(2026, 9, 15));
    }

    @Test
    void nullKindFullNovember_billsTheWholeMonthUntilVacate() {
        OccupancyEntity occupancy = activeOccupancy(
                LocalDate.of(2026, 11, 1), null, new BigDecimal("10000"));
        occupancy.setExpectedExitDate(LocalDate.of(2026, 11, 8));

        assertThat(occupancy.getStayKind()).isNull();
        assertThat(OccupancyBillingCalculator.isBillableInMonth(occupancy, YearMonth.of(2026, 11)))
                .isTrue();
        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(occupancy, YearMonth.of(2026, 11)))
                .isEqualByComparingTo("10000.00");
    }

    @Test
    void explicitMonthlyAndLegacyOpenEnded_useTheSameFullMonthAmount() {
        YearMonth november = YearMonth.of(2026, 11);
        OccupancyEntity monthly = activeOccupancy(LocalDate.of(2026, 11, 1), null, new BigDecimal("10000"));
        monthly.setStayKind(StayKind.MONTHLY);
        // Legacy OPEN_ENDED: pricing_basis null → rent_snapshot is monthly.
        OccupancyEntity openEnded = activeOccupancy(LocalDate.of(2026, 11, 1), null, new BigDecimal("10000"));
        openEnded.setStayKind(StayKind.OPEN_ENDED);

        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(monthly, november))
                .isEqualByComparingTo("10000.00");
        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(openEnded, november))
                .isEqualByComparingTo("10000.00");
    }

    @Test
    void openEndedDailyRate_billsDailyTimesDaysWithoutDividingMonthlyRent() {
        YearMonth november = YearMonth.of(2026, 11);
        OccupancyEntity openEnded = activeOccupancy(LocalDate.of(2026, 11, 1), null, new BigDecimal("200"));
        openEnded.setStayKind(StayKind.OPEN_ENDED);
        openEnded.setPricingBasis(OccupancyPricingBasis.DAILY_RATE);

        // 200/day × 30 days in November = 6000 for a full month (one RENT/MONTHLY row).
        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(openEnded, november))
                .isEqualByComparingTo("6000.00");
    }

    @Test
    void openEndedDailyRate_250PerDay_covers28_30_31DayMonths() {
        OccupancyEntity openEnded = activeOccupancy(LocalDate.of(2026, 1, 1), null, new BigDecimal("250"));
        openEnded.setStayKind(StayKind.OPEN_ENDED);
        openEnded.setPricingBasis(OccupancyPricingBasis.DAILY_RATE);

        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(openEnded, YearMonth.of(2026, 2)))
                .isEqualByComparingTo("7000.00"); // 28 × 250
        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(openEnded, YearMonth.of(2026, 4)))
                .isEqualByComparingTo("7500.00"); // 30 × 250
        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(openEnded, YearMonth.of(2026, 1)))
                .isEqualByComparingTo("7750.00"); // 31 × 250
    }

    @Test
    void openEndedDailyRate_midMonthJoin_isDailyTimesBillableDays() {
        OccupancyEntity openEnded = activeOccupancy(LocalDate.of(2026, 11, 16), null, new BigDecimal("200"));
        openEnded.setStayKind(StayKind.OPEN_ENDED);
        openEnded.setPricingBasis(OccupancyPricingBasis.DAILY_RATE);

        // Nov 16–30 inclusive = 15 days → 200 * 15 = 3000
        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(openEnded, YearMonth.of(2026, 11)))
                .isEqualByComparingTo("3000.00");
    }

    @Test
    void openEndedDailyRate_doesNotAddSeparateMonthlyFoodCharge() {
        OccupancyEntity openEnded = activeOccupancy(LocalDate.of(2026, 11, 1), null, new BigDecimal("200"));
        openEnded.setStayKind(StayKind.OPEN_ENDED);
        openEnded.setPricingBasis(OccupancyPricingBasis.DAILY_RATE);
        openEnded.setFoodEnabled(true);
        openEnded.setFoodIncludedInRent(false);
        openEnded.setFoodChargeSnapshot(new BigDecimal("2500"));

        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(openEnded, YearMonth.of(2026, 11)))
                .isEqualByComparingTo("6000.00");
    }

    @Test
    void openEndedDailyRate_payableRentOnlyAfterPeriodClosesOrVacate() {
        YearMonth november = YearMonth.of(2026, 11);
        OccupancyEntity active = activeOccupancy(LocalDate.of(2026, 11, 5), null, new BigDecimal("1000"));
        active.setStayKind(StayKind.OPEN_ENDED);
        active.setPricingBasis(OccupancyPricingBasis.DAILY_RATE);

        // Mid-month while still active — accrue only.
        assertThat(OccupancyBillingCalculator.shouldGeneratePayableRent(
                        active, november, LocalDate.of(2026, 11, 25)))
                .isFalse();
        // After calendar month closes.
        assertThat(OccupancyBillingCalculator.shouldGeneratePayableRent(
                        active, november, LocalDate.of(2026, 12, 1)))
                .isTrue();

        OccupancyEntity vacated = activeOccupancy(
                LocalDate.of(2026, 11, 5),
                LocalDateTime.of(2026, 11, 12, 12, 0),
                new BigDecimal("1000"));
        vacated.setStayKind(StayKind.OPEN_ENDED);
        vacated.setPricingBasis(OccupancyPricingBasis.DAILY_RATE);
        assertThat(OccupancyBillingCalculator.shouldGeneratePayableRent(
                        vacated, november, LocalDate.of(2026, 11, 12)))
                .isTrue();

        OccupancyEntity monthly = activeOccupancy(LocalDate.of(2026, 11, 5), null, new BigDecimal("10000"));
        monthly.setStayKind(StayKind.MONTHLY);
        assertThat(OccupancyBillingCalculator.shouldGeneratePayableRent(
                        monthly, november, LocalDate.of(2026, 11, 5)))
                .isTrue();
    }

    @Test
    void openEndedDailyRate_openMonthDoesNotContributeToExpectedUntilPayable() {
        YearMonth november = YearMonth.of(2026, 11);
        OccupancyEntity active = activeOccupancy(LocalDate.of(2026, 11, 7), null, new BigDecimal("400"));
        active.setStayKind(StayKind.OPEN_ENDED);
        active.setPricingBasis(OccupancyPricingBasis.DAILY_RATE);

        // Accrual exists for the month, but Expected must wait for a payable obligation.
        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(active, november))
                .isEqualByComparingTo("9600.00"); // 400 × 24 billable days (7–30 Nov)
        assertThat(OccupancyBillingCalculator.contributesToExpectedCharges(
                        active, november, LocalDate.of(2026, 11, 8)))
                .isFalse();
        assertThat(OccupancyBillingCalculator.contributesToExpectedCharges(
                        active, november, LocalDate.of(2026, 12, 1)))
                .isTrue();

        OccupancyEntity monthly = activeOccupancy(LocalDate.of(2026, 11, 6), null, new BigDecimal("8000"));
        monthly.setStayKind(StayKind.MONTHLY);
        assertThat(OccupancyBillingCalculator.contributesToExpectedCharges(
                        monthly, november, LocalDate.of(2026, 11, 8)))
                .isTrue();
    }

    @Test
    void cancelledReservationWithoutMoveIn_doesNotContributeToExpected() {
        OccupancyEntity cancelled = OccupancyEntity.builder()
                .status(OccupancyStatus.VACATED)
                .stayKind(StayKind.MONTHLY)
                .moveInDate(LocalDate.of(2026, 11, 14))
                .vacatedAt(LocalDateTime.of(2026, 11, 7, 17, 33))
                .rentSnapshot(new BigDecimal("6000"))
                .build();
        // No actualMoveInAt → cancelled reservation, not a real checkout.
        assertThat(OccupancyBillingCalculator.isBillableInMonth(cancelled, YearMonth.of(2026, 11)))
                .isFalse();
        assertThat(OccupancyBillingCalculator.contributesToExpectedCharges(
                        cancelled, YearMonth.of(2026, 11), LocalDate.of(2026, 11, 8)))
                .isFalse();
    }

    @Test
    void shortStay_isNotMonthlyRentEvenWhenASnapshotOrAgreedTotalIsPresent() {
        YearMonth november = YearMonth.of(2026, 11);
        OccupancyEntity shortStay = activeOccupancy(LocalDate.of(2026, 11, 1), null, null);
        shortStay.setStayKind(StayKind.SHORT);
        shortStay.setAgreedTotal(new BigDecimal("5600"));

        assertThat(OccupancyBillingCalculator.isBillableInMonth(shortStay, november)).isFalse();
        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(shortStay, november)).isNull();
        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(shortStay)).isNull();

        shortStay.setRentSnapshot(new BigDecimal("5600"));
        assertThat(OccupancyBillingCalculator.isBillableInMonth(shortStay, november)).isFalse();
        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(shortStay, november)).isNull();
    }

    @Test
    void rentChange_usesPriorRentBeforeTheEffectiveMonthAndDoesNotShortenTheMonth() {
        OccupancyEntity occupancy = activeOccupancy(
                LocalDate.of(2026, 9, 1), null, new BigDecimal("11000"));
        occupancy.setStayKind(StayKind.MONTHLY);
        occupancy.setPriorRentSnapshot(new BigDecimal("10000"));
        occupancy.setRentEffectiveMonth(LocalDate.of(2026, 11, 1));
        occupancy.setExpectedExitDate(LocalDate.of(2026, 11, 8));

        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(occupancy, YearMonth.of(2026, 10)))
                .isEqualByComparingTo("10000.00");
        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(occupancy, YearMonth.of(2026, 11)))
                .isEqualByComparingTo("11000.00");
    }

    private static OccupancyEntity activeOccupancy(
            LocalDate moveInDate, LocalDateTime vacatedAt, BigDecimal rent) {
        return OccupancyEntity.builder()
                .status(vacatedAt == null ? OccupancyStatus.ACTIVE : OccupancyStatus.VACATED)
                .moveInDate(moveInDate)
                .actualMoveInAt(moveInDate != null ? moveInDate.atStartOfDay() : null)
                .vacatedAt(vacatedAt)
                .rentSnapshot(rent)
                .build();
    }
}
