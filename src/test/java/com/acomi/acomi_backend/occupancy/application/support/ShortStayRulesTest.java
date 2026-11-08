package com.acomi.acomi_backend.occupancy.application.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acomi.acomi_backend.common.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ShortStayRulesTest {

    @Test
    void sameMonthSevenNightsIsAllowed() {
        ShortStayRules.validateCreate(
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 8),
                new BigDecimal("5600"),
                null);
    }

    @Test
    void dailyRateTimesNightsYieldsStayRent() {
        BigDecimal stayRent = ShortStayRules.resolveStayRent(
                LocalDate.of(2026, 11, 5),
                LocalDate.of(2026, 11, 12),
                new BigDecimal("400"),
                null);
        assertThat(stayRent).isEqualByComparingTo("2800");
    }

    @Test
    void dailyRatePreferredOverLegacyAgreedTotal() {
        BigDecimal stayRent = ShortStayRules.resolveStayRent(
                LocalDate.of(2026, 11, 5),
                LocalDate.of(2026, 11, 12),
                new BigDecimal("500"),
                new BigDecimal("9999"));
        assertThat(stayRent).isEqualByComparingTo("3500");
    }

    @Test
    void spaceFlagOffStillAllowsShortStay() {
        assertThatCode(() -> ShortStayRules.validateCreate(
                        false,
                        LocalDate.of(2026, 11, 1),
                        LocalDate.of(2026, 11, 8),
                        new BigDecimal("5600"),
                        null))
                .doesNotThrowAnyException();
    }

    @Test
    void sameDateIsRejected() {
        assertThatThrownBy(() -> ShortStayRules.validateCreate(
                        LocalDate.of(2026, 11, 1),
                        LocalDate.of(2026, 11, 1),
                        new BigDecimal("5600"),
                        null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("same date");
    }

    @Test
    void nightsInTwoMonthsAreRejected() {
        assertThatThrownBy(() -> ShortStayRules.validateCreate(
                        LocalDate.of(2026, 1, 28),
                        LocalDate.of(2026, 2, 3),
                        new BigDecimal("5600"),
                        null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("more than one calendar month");
    }

    @Test
    void zeroDailyRateIsRejected() {
        assertThatThrownBy(() -> ShortStayRules.resolveStayRent(
                        LocalDate.of(2026, 11, 5),
                        LocalDate.of(2026, 11, 12),
                        BigDecimal.ZERO,
                        null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Daily rent");
    }
}
