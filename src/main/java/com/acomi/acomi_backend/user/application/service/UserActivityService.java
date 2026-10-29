package com.acomi.acomi_backend.user.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Records when an account last signed in and last used ACOMI.
 *
 * <p>Authentication is stateless JWT, so this does not track a live session or online presence.
 * Writes are direct SQL updates and do not bump {@code users.updated_at}. Authenticated requests
 * refresh last use at most once every {@link #ACTIVE_TOUCH_INTERVAL}.
 */
@Service
@RequiredArgsConstructor
public class UserActivityService {

    static final Duration ACTIVE_TOUCH_INTERVAL = Duration.ofMinutes(5);

    private final UserActivityWriter writer;
    private final Clock clock;
    private final ConcurrentMap<UUID, Instant> recentTouches = new ConcurrentHashMap<>();

    public void recordSignIn(UUID userId) {
        if (userId == null) {
            return;
        }
        writer.recordSignIn(userId, LocalDateTime.now(clock));
        recentTouches.put(userId, clock.instant());
    }

    public void touchActivity(UUID userId) {
        if (userId == null) {
            return;
        }
        Instant nowInstant = clock.instant();
        Instant previous = recentTouches.get(userId);
        if (previous != null && Duration.between(previous, nowInstant).compareTo(ACTIVE_TOUCH_INTERVAL) < 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        writer.touch(userId, now, now.minus(ACTIVE_TOUCH_INTERVAL));
        recentTouches.put(userId, nowInstant);
    }
}
