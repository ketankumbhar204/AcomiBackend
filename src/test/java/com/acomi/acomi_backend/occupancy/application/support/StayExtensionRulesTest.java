package com.acomi.acomi_backend.occupancy.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StayExtensionRulesTest {

    @Test
    void checkoutOnTheFirstOfTheNextMonthKeepsTheLastNightInTheOriginalMonth() {
        assertThat(StayExtensionRules.crossesCalendarMonth(
                        LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 1)))
                .isFalse();
    }

    @Test
    void aLaterLastNightMakesTheWholeShortStayCrossAMonth() {
        assertThat(StayExtensionRules.crossesCalendarMonth(
                        LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 2)))
                .isTrue();
        assertThat(StayExtensionRules.crossesCalendarMonth(
                        LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2)))
                .isFalse();
    }
}
