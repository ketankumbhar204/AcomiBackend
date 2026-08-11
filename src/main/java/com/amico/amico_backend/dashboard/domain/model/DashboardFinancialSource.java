package com.amico.amico_backend.dashboard.domain.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DashboardFinancialSource {
    API("API"),
    MEAL_ACTIVITY("MEAL_ACTIVITY"),
    OCCUPANCY("OCCUPANCY"),
    HYBRID("HYBRID");

    private final String value;

    DashboardFinancialSource(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
