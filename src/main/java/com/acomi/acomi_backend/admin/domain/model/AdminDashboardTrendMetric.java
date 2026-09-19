package com.acomi.acomi_backend.admin.domain.model;

/**
 * Daily time-series metrics for the admin dashboard trend chart.
 */
public enum AdminDashboardTrendMetric {
    ENQUIRIES,
    REGISTERED_USERS,
    PROPERTY_REGISTRATIONS,
    MESS_REGISTRATIONS,
    PROPERTY_SPACES,
    MESS_SPACES,
    OWNERS,
    SAVED_ADDRESSES,
    CREDIT_PAYMENTS;

    public static AdminDashboardTrendMetric fromParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return ENQUIRIES;
        }
        String normalized = raw.trim().toUpperCase().replace('-', '_');
        try {
            return AdminDashboardTrendMetric.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return ENQUIRIES;
        }
    }
}
