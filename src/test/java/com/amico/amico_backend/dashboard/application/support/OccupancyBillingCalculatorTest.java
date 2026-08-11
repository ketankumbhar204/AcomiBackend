package com.amico.amico_backend.dashboard.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.amico.amico_backend.occupancy.domain.model.OccupancyStatus;
import com.amico.amico_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
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
    void isBillableInMonth_returnsTrueForMoveInMonthAndLater() {
        OccupancyEntity occupancy = activeOccupancy(
                LocalDate.of(2026, 7, 1), null, new BigDecimal("10000"));

        assertThat(OccupancyBillingCalculator.isBillableInMonth(occupancy, YearMonth.of(2026, 7)))
                .isTrue();
        assertThat(OccupancyBillingCalculator.computeMonthlyExpected(occupancy, YearMonth.of(2026, 7)))
                .isEqualByComparingTo(new BigDecimal("10000"));
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
    void isBillableInMonth_returnsTrueDuringVacatedMonth() {
        OccupancyEntity occupancy = OccupancyEntity.builder()
                .status(OccupancyStatus.VACATED)
                .moveInDate(LocalDate.of(2026, 3, 1))
                .vacatedAt(LocalDateTime.of(2026, 5, 15, 10, 0))
                .rentSnapshot(new BigDecimal("9000"))
                .build();

        assertThat(OccupancyBillingCalculator.isBillableInMonth(occupancy, YearMonth.of(2026, 5)))
                .isTrue();
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
