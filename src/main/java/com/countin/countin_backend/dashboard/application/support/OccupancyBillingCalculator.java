package com.countin.countin_backend.dashboard.application.support;

import com.countin.countin_backend.occupancy.domain.model.OccupancyStatus;
import com.countin.countin_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

public final class OccupancyBillingCalculator {

    private OccupancyBillingCalculator() {}

    public static BigDecimal computeMonthlyExpected(OccupancyEntity occupancy) {
        return computeMonthlyExpected(occupancy, null);
    }

    public static BigDecimal computeMonthlyExpected(OccupancyEntity occupancy, YearMonth month) {
        if (month != null && !isBillableInMonth(occupancy, month)) {
            return null;
        }
        return computeContractMonthlyTotal(occupancy);
    }

    public static boolean isBillableInMonth(OccupancyEntity occupancy, YearMonth month) {
        if (occupancy.getStatus() == OccupancyStatus.RESERVED) {
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

    private static BigDecimal computeContractMonthlyTotal(OccupancyEntity occupancy) {
        if (occupancy.getRentSnapshot() == null) {
            return null;
        }

        if (occupancy.isFoodIncludedInRent()) {
            return occupancy.getRentSnapshot();
        }

        if (!occupancy.isFoodEnabled()) {
            return occupancy.getRentSnapshot();
        }

        BigDecimal food = occupancy.getFoodChargeSnapshot() != null
                ? occupancy.getFoodChargeSnapshot()
                : BigDecimal.ZERO;
        return occupancy.getRentSnapshot().add(food);
    }
}
