package com.acomi.acomi_backend.notification.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OverdueStayAlertRulesTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 11, 8);

    @Test
    void plannedEndBeforeBusinessDate_isOverdue() {
        assertThat(OverdueStayAlertRules.isOverdue(
                OccupancyStatus.ACTIVE, null, LocalDate.of(2026, 11, 7), BUSINESS_DATE))
                .isTrue();
    }

    @Test
    void plannedEndOnBusinessDate_isNotOverdue() {
        assertThat(OverdueStayAlertRules.isOverdue(
                OccupancyStatus.ACTIVE, null, BUSINESS_DATE, BUSINESS_DATE))
                .isFalse();
    }

    @Test
    void futurePlannedEnd_isNotOverdue() {
        assertThat(OverdueStayAlertRules.isOverdue(
                OccupancyStatus.ACTIVE, null, LocalDate.of(2026, 11, 9), BUSINESS_DATE))
                .isFalse();
    }

    @Test
    void inactiveOrVacatedStay_isNotOverdue() {
        LocalDate yesterday = LocalDate.of(2026, 11, 7);
        assertThat(OverdueStayAlertRules.isOverdue(
                OccupancyStatus.RESERVED, null, yesterday, BUSINESS_DATE)).isFalse();
        assertThat(OverdueStayAlertRules.isOverdue(
                OccupancyStatus.VACATED, null, yesterday, BUSINESS_DATE)).isFalse();
        assertThat(OverdueStayAlertRules.isOverdue(
                OccupancyStatus.ACTIVE, LocalDateTime.of(2026, 11, 7, 10, 0), yesterday, BUSINESS_DATE))
                .isFalse();
        assertThat(OverdueStayAlertRules.isOverdue(
                OccupancyStatus.ACTIVE, null, null, BUSINESS_DATE)).isFalse();
    }

    @Test
    void dedupeKey_isStableForOneStayAndBusinessDay_andChangesTheNextDay() {
        UUID occupancyId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        String today = OverdueStayAlertRules.dedupeKey(occupancyId, BUSINESS_DATE, userId);
        String retry = OverdueStayAlertRules.dedupeKey(occupancyId, BUSINESS_DATE, userId);
        String nextDay = OverdueStayAlertRules.dedupeKey(occupancyId, BUSINESS_DATE.plusDays(1), userId);
        assertThat(today).isEqualTo(retry);
        assertThat(today).isNotEqualTo(nextDay);
        assertThat(today).startsWith("STAY_OVERDUE_ACTIVE:");
    }

    @Test
    void blankTimezone_usesKolkataAcrossTheUtcEveningBoundary() {
        SpaceEntity space = SpaceEntity.builder().name("Sunrise").timezone(" ").build();
        Clock beforeMidnight = Clock.fixed(Instant.parse("2026-10-29T18:29:00Z"), ZoneOffset.UTC);
        Clock atMidnight = Clock.fixed(Instant.parse("2026-10-29T18:30:00Z"), ZoneOffset.UTC);
        assertThat(OverdueStayAlertRules.businessDate(space, beforeMidnight))
                .isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(OverdueStayAlertRules.businessDate(space, atMidnight))
                .isEqualTo(LocalDate.of(2026, 10, 30));
    }

    @Test
    void asiaKolkata_isTheDayAfterUtcWhenTheLocalDateHasRolled() {
        SpaceEntity space = SpaceEntity.builder().name("Sunrise").timezone("Asia/Kolkata").build();
        Clock clock = Clock.fixed(Instant.parse("2026-10-29T20:00:00Z"), ZoneOffset.UTC);
        LocalDate businessDate = OverdueStayAlertRules.businessDate(space, clock);
        assertThat(businessDate).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(OverdueStayAlertRules.isOverdue(
                OccupancyStatus.ACTIVE, null, LocalDate.of(2026, 10, 29), businessDate)).isTrue();
        assertThat(OverdueStayAlertRules.isOverdue(
                OccupancyStatus.ACTIVE, null, businessDate, businessDate)).isFalse();
    }
}
