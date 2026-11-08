package com.acomi.acomi_backend.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class OpenEndedPrematureRentReconciliationServiceTest {

    @Test
    void midMonthActivationBill_isPremature() {
        SpacePaymentEntity payment = SpacePaymentEntity.builder()
                .billingPeriodEnd(LocalDate.of(2026, 11, 30))
                .build();
        payment.setCreatedAt(LocalDateTime.of(2026, 11, 5, 10, 0));

        assertThat(OpenEndedPrematureRentReconciliationService.isPremature(payment)).isTrue();
    }

    @Test
    void monthCloseBill_isNotPremature() {
        SpacePaymentEntity payment = SpacePaymentEntity.builder()
                .billingPeriodEnd(LocalDate.of(2026, 11, 30))
                .build();
        payment.setCreatedAt(LocalDateTime.of(2026, 12, 1, 0, 40));

        assertThat(OpenEndedPrematureRentReconciliationService.isPremature(payment)).isFalse();
    }

    @Test
    void vacateSettle_isNotPremature() {
        OccupancyEntity occupancy = OccupancyEntity.builder()
                .status(OccupancyStatus.VACATED)
                .vacatedAt(LocalDateTime.of(2026, 11, 12, 12, 0))
                .build();
        SpacePaymentEntity payment = SpacePaymentEntity.builder()
                .occupancy(occupancy)
                .billingPeriodEnd(LocalDate.of(2026, 11, 12))
                .remarks("Created on vacate for actual occupied days")
                .build();
        payment.setCreatedAt(LocalDateTime.of(2026, 11, 12, 12, 5));

        assertThat(OpenEndedPrematureRentReconciliationService.isPremature(payment)).isFalse();
    }
}
