package com.acomi.acomi_backend.dashboard.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
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

    private static OccupancyEntity activeOccupancy(
            LocalDate moveInDate, LocalDateTime vacatedAt, BigDecimal rent) {
        return OccupancyEntity.builder()
                .status(vacatedAt == null ? OccupancyStatus.ACTIVE : OccupancyStatus.VACATED)
                .moveInDate(moveInDate)
                .vacatedAt(vacatedAt)
                .rentSnapshot(rent)
                .build();
    }
}
