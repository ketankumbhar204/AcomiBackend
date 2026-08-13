package com.acomi.acomi_backend.dashboard.domain.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DashboardAttentionKind {
    NOT_PLANNED("not_planned"),
    PARTIAL_PLANNED("partial_planned"),
    READY_TO_SHARE("ready_to_share"),
    POLL_OPEN("poll_open"),
    PAYMENTS_OVERDUE("payments_overdue"),
    SUBSCRIPTION_ACTIVATION_PENDING("subscription_activation_pending");

    private final String value;

    DashboardAttentionKind(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
