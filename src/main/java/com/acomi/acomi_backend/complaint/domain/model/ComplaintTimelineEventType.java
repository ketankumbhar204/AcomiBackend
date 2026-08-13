package com.acomi.acomi_backend.complaint.domain.model;

public enum ComplaintTimelineEventType {
    CREATED,
    STATUS_CHANGED,
    COMMENTED,
    INTERNAL_NOTE,
    ATTACHMENT_ADDED,
    ASSIGNED,
    PRIORITY_CHANGED,
    REOPENED,
    RESOLVED,
    CLOSED,
    CANCELLED
}
