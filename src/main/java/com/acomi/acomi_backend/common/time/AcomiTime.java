package com.acomi.acomi_backend.common.time;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** ACOMI stores civil times in India Standard Time and exposes them with that offset. */
public final class AcomiTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private AcomiTime() {}

    public static OffsetDateTime toOffset(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ZONE).toOffsetDateTime();
    }
}
