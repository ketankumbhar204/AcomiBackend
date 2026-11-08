package com.acomi.acomi_backend.occupancy.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class StayAvailabilityRulesTest {

    private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);
    private static final LocalDate NOV_8 = LocalDate.of(2026, 11, 8);
    private static final LocalDate NOV_10 = LocalDate.of(2026, 11, 10);

    @Test
    void adjacentHalfOpenRangesDoNotOverlap() {
        assertThat(StayAvailabilityRules.overlaps(NOV_1, NOV_8, NOV_8, NOV_10)).isFalse();
        assertThat(StayAvailabilityRules.overlaps(NOV_8, NOV_10, NOV_1, NOV_8)).isFalse();
    }

    @Test
    void overlappingHalfOpenRangesConflict() {
        assertThat(StayAvailabilityRules.overlaps(NOV_1, NOV_8, NOV_1.plusDays(6), NOV_10)).isTrue();
    }

    @Test
    void nullEndBlocksEveryLaterDate() {
        assertThat(StayAvailabilityRules.overlaps(NOV_1, null, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 8)))
                .isTrue();
    }

    @Test
    void emptyRangeBlocksNothing() {
        assertThat(StayAvailabilityRules.overlaps(NOV_1, NOV_1, NOV_1, NOV_8)).isFalse();
    }

    @Test
    void vacatedStayEndsOnTheVacateDate() {
        OccupancyEntity vacated = OccupancyEntity.builder()
                .status(OccupancyStatus.VACATED)
                .moveInDate(NOV_1)
                .expectedExitDate(NOV_10)
                .build();
        vacated.setVacatedAt(NOV_8.atStartOfDay());

        assertThat(StayAvailabilityRules.blockingEnd(vacated)).isEqualTo(NOV_8);
        assertThat(StayAvailabilityRules.overlaps(NOV_8, NOV_10, StayAvailabilityRules.blockingStart(vacated), NOV_8))
                .isFalse();
        assertThat(StayAvailabilityRules.overlaps(NOV_1.plusDays(6), NOV_10, NOV_1, NOV_8)).isTrue();
    }

    @Test
    void activeStayWithNoPlannedEndIsUnbounded() {
        OccupancyEntity open = OccupancyEntity.builder()
                .status(OccupancyStatus.ACTIVE)
                .moveInDate(NOV_1)
                .build();
        open.setVacatedAt(LocalDateTime.of(2026, 11, 8, 9, 0));

        assertThat(StayAvailabilityRules.blockingEnd(open)).isNull();
    }
}
