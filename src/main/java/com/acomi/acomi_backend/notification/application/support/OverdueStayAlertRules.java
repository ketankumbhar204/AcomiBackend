package com.acomi.acomi_backend.notification.application.support;

import com.acomi.acomi_backend.meal.application.support.MealPollCloseAtCalculator;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * D-10. An active stay is overdue when its planned end is strictly before the
 * space business date. The checkout-today alert keeps the JVM date and is not
 * decided here.
 */
public final class OverdueStayAlertRules {

    private OverdueStayAlertRules() {
    }

    public static LocalDate businessDate(SpaceEntity space, Clock clock) {
        return LocalDateTime.now(clock.withZone(MealPollCloseAtCalculator.zoneOf(space))).toLocalDate();
    }

    public static boolean isOverdue(
            OccupancyStatus status,
            LocalDateTime vacatedAt,
            LocalDate plannedEnd,
            LocalDate businessDate) {
        return status == OccupancyStatus.ACTIVE
                && vacatedAt == null
                && plannedEnd != null
                && businessDate != null
                && plannedEnd.isBefore(businessDate);
    }

    /** One open alert per stay, per business date, per recipient. */
    public static String dedupeKey(UUID occupancyId, LocalDate businessDate, UUID userId) {
        return NotificationType.STAY_OVERDUE_ACTIVE.name()
                + ":"
                + occupancyId
                + ":"
                + businessDate
                + ":"
                + userId;
    }
}
