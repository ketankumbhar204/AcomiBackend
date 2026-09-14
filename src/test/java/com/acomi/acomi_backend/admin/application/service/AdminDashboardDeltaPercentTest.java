package com.acomi.acomi_backend.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdminDashboardDeltaPercentTest {

    @Test
    void deltaPercent_comparesSelectedPeriodAgainstPreviousEquivalentPeriod() {
        assertThat(AdminDashboardService.deltaPercent(12, 10)).isEqualTo(20.0);
        assertThat(AdminDashboardService.deltaPercent(0, 0)).isEqualTo(0.0);
        assertThat(AdminDashboardService.deltaPercent(5, 0)).isEqualTo(100.0);
        assertThat(AdminDashboardService.deltaPercent(8, 10)).isEqualTo(-20.0);
    }
}
