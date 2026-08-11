package com.amico.amico_backend.complaint.domain.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Allowed status transitions for Complaints MVP.
 *
 * <pre>
 * OPEN → IN_PROGRESS | RESOLVED | CANCELLED
 * IN_PROGRESS → RESOLVED | CANCELLED | OPEN (re-open work)
 * RESOLVED → CLOSED | OPEN (via reopen policy, not this map alone)
 * CLOSED → (terminal)
 * CANCELLED → (terminal)
 * </pre>
 */
public final class ComplaintStatusTransition {

    private static final Map<ComplaintStatus, Set<ComplaintStatus>> ALLOWED =
            new EnumMap<>(ComplaintStatus.class);

    static {
        ALLOWED.put(
                ComplaintStatus.OPEN,
                EnumSet.of(
                        ComplaintStatus.IN_PROGRESS,
                        ComplaintStatus.RESOLVED,
                        ComplaintStatus.CANCELLED));
        ALLOWED.put(
                ComplaintStatus.IN_PROGRESS,
                EnumSet.of(
                        ComplaintStatus.RESOLVED,
                        ComplaintStatus.CANCELLED,
                        ComplaintStatus.OPEN));
        ALLOWED.put(ComplaintStatus.RESOLVED, EnumSet.of(ComplaintStatus.CLOSED));
        ALLOWED.put(ComplaintStatus.CLOSED, EnumSet.noneOf(ComplaintStatus.class));
        ALLOWED.put(ComplaintStatus.CANCELLED, EnumSet.noneOf(ComplaintStatus.class));
    }

    private ComplaintStatusTransition() {}

    public static boolean canTransition(ComplaintStatus from, ComplaintStatus to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(ComplaintStatus.class)).contains(to);
    }
}
