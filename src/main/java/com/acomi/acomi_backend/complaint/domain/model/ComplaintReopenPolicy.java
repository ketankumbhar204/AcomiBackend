package com.acomi.acomi_backend.complaint.domain.model;

import java.time.LocalDateTime;

/**
 * MVP reopen rules: only from RESOLVED, within a fixed window after resolved_at.
 * CLOSED and CANCELLED are terminal.
 */
public final class ComplaintReopenPolicy {

    public static final int REOPEN_WINDOW_DAYS = 7;

    private ComplaintReopenPolicy() {}

    public static boolean canReopen(ComplaintStatus status, LocalDateTime resolvedAt, LocalDateTime now) {
        if (status != ComplaintStatus.RESOLVED || resolvedAt == null || now == null) {
            return false;
        }
        return !now.isAfter(resolvedAt.plusDays(REOPEN_WINDOW_DAYS));
    }
}
