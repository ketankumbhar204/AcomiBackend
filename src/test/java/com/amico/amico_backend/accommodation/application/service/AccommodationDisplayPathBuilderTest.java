package com.amico.amico_backend.accommodation.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.amico.amico_backend.accommodation.domain.model.AccommodationStatus;
import com.amico.amico_backend.accommodation.infrastructure.persistence.projection.AllocationTargetSearchRow;
import com.amico.amico_backend.occupancy.domain.model.AllocationTargetType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccommodationDisplayPathBuilderTest {

    @Test
    void buildDisplayPath_includesFullBedPath() {
        AllocationTargetSearchRow row = bedRow("Building A", "Floor 1", null, "Room 101", "Bed A");

        assertThat(AccommodationDisplayPathBuilder.buildDisplayPath(row))
                .isEqualTo("Building A · Floor 1 · Room 101 · Bed A");
    }

    @Test
    void buildDisplayPath_unitTargetShowsBuildingAndUnit() {
        AllocationTargetSearchRow row = unitRow("Tower A", "Unit 101");

        assertThat(AccommodationDisplayPathBuilder.buildDisplayPath(row)).isEqualTo("Tower A · Unit 101");
    }

    private static AllocationTargetSearchRow bedRow(
            String building, String floor, String unit, String room, String bed) {
        return new AllocationTargetSearchRow(
                AllocationTargetType.BED,
                UUID.randomUUID(),
                UUID.randomUUID(),
                building,
                "A",
                UUID.randomUUID(),
                floor,
                unit != null ? UUID.randomUUID() : null,
                unit,
                null,
                UUID.randomUUID(),
                room,
                "101",
                UUID.randomUUID(),
                bed,
                "A",
                AccommodationStatus.AVAILABLE,
                new BigDecimal("8000"),
                BigDecimal.ZERO);
    }

    private static AllocationTargetSearchRow unitRow(String building, String unit) {
        return new AllocationTargetSearchRow(
                AllocationTargetType.UNIT,
                UUID.randomUUID(),
                UUID.randomUUID(),
                building,
                "T",
                null,
                null,
                UUID.randomUUID(),
                unit,
                "101",
                null,
                null,
                null,
                null,
                null,
                null,
                AccommodationStatus.AVAILABLE,
                new BigDecimal("15000"),
                new BigDecimal("10000"));
    }
}
