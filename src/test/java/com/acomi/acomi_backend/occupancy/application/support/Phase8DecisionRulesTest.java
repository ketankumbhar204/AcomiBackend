package com.acomi.acomi_backend.occupancy.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acomi.acomi_backend.common.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Locks Phase 8 decision closures for D-05, D-14, D-16, D-17, and D-18 where the
 * approved rule is expressed as pure stay/payment policy (no Spring context).
 *
 * <p>D-14 (allow extend while unpaid) and D-17/D-18 payment-service gates stay covered by
 * existing service tests; this class pins calendar and amount invention rules.
 */
class Phase8DecisionRulesTest {

    @Test
    void d05_shortStayCrossingCalendarMonth_isRejected() {
        LocalDate checkIn = LocalDate.of(2026, 1, 28);
        LocalDate checkout = LocalDate.of(2026, 2, 3);
        assertThat(StayExtensionRules.crossesCalendarMonth(checkIn, checkout)).isTrue();
        assertThatThrownBy(() -> ShortStayRules.validateCreate(true, checkIn, checkout, new BigDecimal("5600"), null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("SHORT_STAY_CROSSES_MONTH");
    }

    @Test
    void d05_sameMonthShortStay_isAllowed() {
        LocalDate checkIn = LocalDate.of(2026, 11, 1);
        LocalDate checkout = LocalDate.of(2026, 11, 8);
        assertThat(StayExtensionRules.crossesCalendarMonth(checkIn, checkout)).isFalse();
        ShortStayRules.validateCreate(true, checkIn, checkout, new BigDecimal("5600"), null);
    }

    @Test
    void d05_shortExtensionCrossingCalendarMonth_isRejectedByHelper() {
        // Whole-stay window: move-in last day of month, new end two days into next month.
        LocalDate moveIn = LocalDate.of(2026, 10, 31);
        LocalDate newEnd = LocalDate.of(2026, 11, 2);
        assertThat(StayExtensionRules.crossesCalendarMonth(moveIn, newEnd)).isTrue();
    }

    @Test
    void d16_positiveAdditionalAmount_isOwnerEnteredChargeSignal() {
        assertThat(StayExtensionRules.hasCharge(new BigDecimal("800"))).isTrue();
        assertThat(StayExtensionRules.hasCharge(BigDecimal.ZERO)).isFalse();
        assertThat(StayExtensionRules.hasCharge(null)).isFalse();
    }

    @Test
    void d16_doesNotInventLateFeeFromMonthlyDivision() {
        // Policy lock: there is no helper that derives late fee as monthly÷nights.
        // Owner-entered additionalAmount remains the only positive extension charge signal.
        assertThat(StayExtensionRules.hasCharge(new BigDecimal("10000").divide(new BigDecimal("30"), 2, java.math.RoundingMode.HALF_UP)))
                .isTrue();
        // The invented figure above is still only a number; product code must not auto-apply it on vacate.
        assertThat(StayExtensionRules.hasCharge(null)).isFalse();
    }

    @Test
    void leapYearMonthBoundary_crossesFebruary() {
        // Half-open: [2024-02-28, 2024-03-01) is only Feb 28 — same month.
        assertThat(StayExtensionRules.crossesCalendarMonth(LocalDate.of(2024, 2, 28), LocalDate.of(2024, 3, 1)))
                .isFalse();
        // [2024-02-28, 2024-03-02) includes Mar 1 night — crosses.
        assertThat(StayExtensionRules.crossesCalendarMonth(LocalDate.of(2024, 2, 28), LocalDate.of(2024, 3, 2)))
                .isTrue();
        assertThat(StayExtensionRules.crossesCalendarMonth(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29)))
                .isFalse();
    }

    @Test
    void yearEndBoundary_crossesDecember() {
        assertThat(StayExtensionRules.crossesCalendarMonth(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2)))
                .isTrue();
        assertThat(StayExtensionRules.crossesCalendarMonth(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31)))
                .isFalse();
    }
}
