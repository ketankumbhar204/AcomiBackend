package com.countin.countin_backend.meal.application.job;

import com.countin.countin_backend.meal.application.service.MealPollService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically closes OPEN meal polls whose {@code pollCloseAt} has passed.
 * Reuses {@link MealPollService#closeExpiredOpenPolls()} — same mutation as manual Close Poll.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MealPollAutoCloseJob {

    private final MealPollService mealPollService;

    /** Every minute; cheap no-op when nothing is due. */
    @Scheduled(fixedDelayString = "${countin.meal-poll.auto-close-delay-ms:60000}")
    public void closeExpiredPolls() {
        try {
            int closed = mealPollService.closeExpiredOpenPolls();
            if (closed > 0) {
                log.info("Auto-closed {} meal poll(s)", closed);
            }
        } catch (Exception ex) {
            log.error("Meal poll auto-close job failed", ex);
        }
    }
}
