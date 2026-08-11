package com.amico.amico_backend.meal.application.support;

import com.amico.amico_backend.meal.domain.model.MealType;
import com.amico.amico_backend.meal.domain.model.PollCloseDayOffset;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Resolves default {@code pollCloseAt} from space meal-slot defaults in the space timezone.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MealPollCloseAtCalculator {

    public static final String DEFAULT_TIMEZONE = "Asia/Kolkata";

    public static ZoneId zoneOf(SpaceEntity space) {
        String tz = space.getTimezone();
        if (tz == null || tz.isBlank()) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
        try {
            return ZoneId.of(tz);
        } catch (Exception ignored) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
    }

    public static LocalDateTime nowInSpace(SpaceEntity space) {
        return LocalDateTime.now(zoneOf(space));
    }

    public static LocalDateTime resolveDefaultCloseAt(
            SpaceEntity space, LocalDate pollDate, MealType mealType) {
        PollCloseDayOffset offset = dayOffset(space, mealType);
        LocalTime time = closeTime(space, mealType);
        LocalDate closeDate = offset == PollCloseDayOffset.PREVIOUS_DAY
                ? pollDate.minusDays(1)
                : pollDate;
        return LocalDateTime.of(closeDate, time);
    }

    /**
     * Close time used when opening/reopening a poll.
     * Ensures at least {@link #MIN_OPEN_RESPONSE_HOURS} hours remain so polls shared
     * near the configured deadline (e.g. tomorrow's Breakfast with previous-day 20:00)
     * are not auto-closed before customers can respond.
     */
    public static final int MIN_OPEN_RESPONSE_HOURS = 4;

    public static LocalDateTime resolveOpenPollCloseAt(
            SpaceEntity space, LocalDate pollDate, MealType mealType) {
        LocalDateTime configured = resolveDefaultCloseAt(space, pollDate, mealType);
        LocalDateTime now = nowInSpace(space);
        LocalDateTime minimumClose = now.plusHours(MIN_OPEN_RESPONSE_HOURS);
        if (configured.isAfter(minimumClose)) {
            return configured;
        }
        return minimumClose;
    }

    private static PollCloseDayOffset dayOffset(SpaceEntity space, MealType mealType) {
        return switch (mealType) {
            case BREAKFAST -> coalesce(
                    space.getPollCloseBreakfastDayOffset(), PollCloseDayOffset.PREVIOUS_DAY);
            case LUNCH -> coalesce(space.getPollCloseLunchDayOffset(), PollCloseDayOffset.SAME_DAY);
            case DINNER -> coalesce(space.getPollCloseDinnerDayOffset(), PollCloseDayOffset.SAME_DAY);
        };
    }

    private static LocalTime closeTime(SpaceEntity space, MealType mealType) {
        return switch (mealType) {
            case BREAKFAST -> coalesce(space.getPollCloseBreakfastTime(), LocalTime.of(20, 0));
            case LUNCH -> coalesce(space.getPollCloseLunchTime(), LocalTime.of(8, 0));
            case DINNER -> coalesce(space.getPollCloseDinnerTime(), LocalTime.of(13, 0));
        };
    }

    private static <T> T coalesce(T value, T fallback) {
        return value != null ? value : fallback;
    }
}
