package com.acomi.acomi_backend.meal.application.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.acomi.acomi_backend.meal.domain.model.MealType;
import com.acomi.acomi_backend.meal.domain.model.PollCloseDayOffset;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class MealPollCloseAtCalculatorTest {

    @Test
    void breakfastDefaultsToPreviousDayEvening() {
        SpaceEntity space = SpaceEntity.builder()
                .timezone("Asia/Kolkata")
                .pollCloseBreakfastDayOffset(PollCloseDayOffset.PREVIOUS_DAY)
                .pollCloseBreakfastTime(LocalTime.of(20, 0))
                .build();
        LocalDateTime closeAt = MealPollCloseAtCalculator.resolveDefaultCloseAt(
                space, LocalDate.of(2026, 7, 12), MealType.BREAKFAST);
        assertEquals(LocalDateTime.of(2026, 7, 11, 20, 0), closeAt);
    }

    @Test
    void lunchDefaultsToSameDayMorning() {
        SpaceEntity space = SpaceEntity.builder()
                .timezone("Asia/Kolkata")
                .pollCloseLunchDayOffset(PollCloseDayOffset.SAME_DAY)
                .pollCloseLunchTime(LocalTime.of(8, 0))
                .build();
        LocalDateTime closeAt = MealPollCloseAtCalculator.resolveDefaultCloseAt(
                space, LocalDate.of(2026, 7, 12), MealType.LUNCH);
        assertEquals(LocalDateTime.of(2026, 7, 12, 8, 0), closeAt);
    }

    @Test
    void dinnerDefaultsToSameDayAfternoon() {
        SpaceEntity space = SpaceEntity.builder()
                .timezone("Asia/Kolkata")
                .pollCloseDinnerDayOffset(PollCloseDayOffset.SAME_DAY)
                .pollCloseDinnerTime(LocalTime.of(13, 0))
                .build();
        LocalDateTime closeAt = MealPollCloseAtCalculator.resolveDefaultCloseAt(
                space, LocalDate.of(2026, 7, 12), MealType.DINNER);
        assertEquals(LocalDateTime.of(2026, 7, 12, 13, 0), closeAt);
    }

    @Test
    void openPollCloseAtExtendsWhenConfiguredDeadlineAlreadyPast() {
        SpaceEntity space = SpaceEntity.builder()
                .timezone("Asia/Kolkata")
                .pollCloseBreakfastDayOffset(PollCloseDayOffset.PREVIOUS_DAY)
                .pollCloseBreakfastTime(LocalTime.of(20, 0))
                .build();
        LocalDate pollDate = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        LocalDateTime configured = MealPollCloseAtCalculator.resolveDefaultCloseAt(
                space, pollDate, MealType.BREAKFAST);
        LocalDateTime openCloseAt =
                MealPollCloseAtCalculator.resolveOpenPollCloseAt(space, pollDate, MealType.BREAKFAST);
        LocalDateTime now = MealPollCloseAtCalculator.nowInSpace(space);
        LocalDateTime minimumClose =
                now.plusHours(MealPollCloseAtCalculator.MIN_OPEN_RESPONSE_HOURS);
        if (!configured.isAfter(minimumClose)) {
            org.junit.jupiter.api.Assertions.assertFalse(openCloseAt.isBefore(minimumClose.minusSeconds(1)));
        } else {
            assertEquals(configured, openCloseAt);
        }
    }

    @Test
    void openPollCloseAtExtendsWhenConfiguredDeadlineIsSoon() {
        // Tomorrow's breakfast with previous-day 20:00 closes ~1h after a 19:00 share —
        // must extend so customers still see Breakfast on the dashboard.
        SpaceEntity space = SpaceEntity.builder()
                .timezone("Asia/Kolkata")
                .pollCloseBreakfastDayOffset(PollCloseDayOffset.PREVIOUS_DAY)
                .pollCloseBreakfastTime(LocalTime.of(20, 0))
                .build();
        LocalDateTime now = MealPollCloseAtCalculator.nowInSpace(space);
        LocalDate pollDate = now.toLocalDate().plusDays(1);
        LocalDateTime configured = MealPollCloseAtCalculator.resolveDefaultCloseAt(
                space, pollDate, MealType.BREAKFAST);
        LocalDateTime openCloseAt =
                MealPollCloseAtCalculator.resolveOpenPollCloseAt(space, pollDate, MealType.BREAKFAST);
        LocalDateTime minimumClose =
                now.plusHours(MealPollCloseAtCalculator.MIN_OPEN_RESPONSE_HOURS);
        if (configured.isAfter(minimumClose)) {
            assertEquals(configured, openCloseAt);
        } else {
            org.junit.jupiter.api.Assertions.assertFalse(openCloseAt.isBefore(minimumClose.minusSeconds(1)));
            org.junit.jupiter.api.Assertions.assertTrue(openCloseAt.isAfter(configured));
        }
    }
}
