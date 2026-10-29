package com.acomi.acomi_backend.user.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.acomi.acomi_backend.common.time.AcomiTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserActivityServiceTest {

    @Mock
    private UserActivityWriter writer;

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-17T11:46:00Z"));

    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return AcomiTime.ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    private UserActivityService service;

    @BeforeEach
    void setUp() {
        service = new UserActivityService(writer, clock);
    }

    @Test
    void recordSignIn_storesKolkataLocalTime() {
        UUID userId = UUID.randomUUID();

        service.recordSignIn(userId);

        verify(writer).recordSignIn(userId, LocalDateTime.of(2026, 10, 17, 17, 16));
    }

    @Test
    void touch_skipsARepeatInsideFiveMinutes() {
        UUID userId = UUID.randomUUID();

        service.touchActivity(userId);
        now.set(now.get().plus(4, ChronoUnit.MINUTES));
        service.touchActivity(userId);

        verify(writer, times(1))
                .touch(
                        eq(userId),
                        eq(LocalDateTime.of(2026, 10, 17, 17, 16)),
                        eq(LocalDateTime.of(2026, 10, 17, 17, 11)));
    }

    @Test
    void touch_writesAgainAfterFiveMinutes() {
        UUID userId = UUID.randomUUID();

        service.touchActivity(userId);
        now.set(now.get().plus(6, ChronoUnit.MINUTES));
        service.touchActivity(userId);

        verify(writer).touch(userId, LocalDateTime.of(2026, 10, 17, 17, 22), LocalDateTime.of(2026, 10, 17, 17, 17));
    }

    @Test
    void signIn_suppressesTheFollowingActivityTouch() {
        UUID userId = UUID.randomUUID();

        service.recordSignIn(userId);
        service.touchActivity(userId);

        verify(writer, never()).touch(any(), any(), any());
    }

    @Test
    void nullUser_doesNotWrite() {
        service.recordSignIn(null);
        service.touchActivity(null);

        verifyNoInteractions(writer);
    }
}
