package com.acomi.acomi_backend.occupancy.application.support;

import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import java.time.LocalDate;

/**
 * Half-open stay ranges. A null planned end blocks every later date.
 * A vacated stay blocks only until the vacate date, which is free for the next stay.
 */
public final class StayAvailabilityRules {

    public static final String OVERLAP =
            "This stay overlaps another stay on that bed, room, or unit.";

    private StayAvailabilityRules() {}

    public static boolean overlaps(
            LocalDate start, LocalDate exclusiveEnd, LocalDate otherStart, LocalDate otherExclusiveEnd) {
        if (start == null || otherStart == null) {
            return false;
        }
        if (exclusiveEnd != null && !exclusiveEnd.isAfter(start)) {
            return false;
        }
        if (otherExclusiveEnd != null && !otherExclusiveEnd.isAfter(otherStart)) {
            return false;
        }
        boolean startsBeforeOtherEnds = otherExclusiveEnd == null || start.isBefore(otherExclusiveEnd);
        boolean otherStartsBeforeEnd = exclusiveEnd == null || otherStart.isBefore(exclusiveEnd);
        return startsBeforeOtherEnds && otherStartsBeforeEnd;
    }

    public static LocalDate blockingStart(OccupancyEntity occupancy) {
        return occupancy.getMoveInDate();
    }

    public static LocalDate blockingEnd(OccupancyEntity occupancy) {
        if (occupancy.getStatus() == OccupancyStatus.VACATED) {
            return occupancy.getVacatedAt() == null ? null : occupancy.getVacatedAt().toLocalDate();
        }
        if (occupancy.getExpectedExitDate() != null) {
            return occupancy.getExpectedExitDate();
        }
        return occupancy.getExpectedCheckoutDate();
    }
}
