package com.amico.amico_backend.dashboard.domain.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum MemberPaymentStatus {
    PAID("PAID"),
    PARTIAL("PARTIAL"),
    PENDING("PENDING"),
    UNDER_REVIEW("UNDER_REVIEW"),
    UPDATE_REQUESTED("UPDATE_REQUESTED"),
    REJECTED("REJECTED"),
    NONE("NONE");

    private final String value;

    MemberPaymentStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
