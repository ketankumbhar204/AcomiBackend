package com.acomi.acomi_backend.admin.application.support;

import com.acomi.acomi_backend.common.exception.RateLimitedException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Stops a double-click and a burst of test sends. One admin at a time, with a short gap
 * and a rolling cap. Does not apply to other users.
 */
@Component
public class AdminNotificationTestRateLimit {

    static final int MAX_PER_WINDOW = 20;
    static final long WINDOW_SECONDS = 600;
    static final long MIN_GAP_SECONDS = 3;

    private final Clock clock;
    private final Map<UUID, Deque<Instant>> recent = new ConcurrentHashMap<>();
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public AdminNotificationTestRateLimit(Clock clock) {
        this.clock = clock;
    }

    public void acquire(UUID adminId) {
        if (!inFlight.add(adminId)) {
            throw new RateLimitedException("A test notification is already being sent.", MIN_GAP_SECONDS);
        }
        Instant now = clock.instant();
        Deque<Instant> stamps = recent.computeIfAbsent(adminId, ignored -> new ArrayDeque<>());
        synchronized (stamps) {
            Instant cutoff = now.minusSeconds(WINDOW_SECONDS);
            while (!stamps.isEmpty() && stamps.peekFirst().isBefore(cutoff)) {
                stamps.removeFirst();
            }
            if (!stamps.isEmpty()) {
                long sinceLast = now.getEpochSecond() - stamps.peekLast().getEpochSecond();
                if (sinceLast < MIN_GAP_SECONDS) {
                    inFlight.remove(adminId);
                    throw new RateLimitedException(
                            "Wait a moment before sending another test notification.",
                            MIN_GAP_SECONDS - sinceLast);
                }
            }
            if (stamps.size() >= MAX_PER_WINDOW) {
                inFlight.remove(adminId);
                throw new RateLimitedException(
                        "Test notification limit reached. Try again in a few minutes.",
                        WINDOW_SECONDS);
            }
            stamps.addLast(now);
        }
    }

    public void release(UUID adminId) {
        inFlight.remove(adminId);
    }
}
