package com.acomi.acomi_backend.occupancy.application.service;

import com.acomi.acomi_backend.accommodation.domain.model.PropertyLayoutMode;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.FloorEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.UnitEntity;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

/**
 * Builds human-readable accommodation trails for payment cards and similar summaries.
 * Mirrors the visible hierarchy from accommodation UI profiles (layout-mode aware).
 */
@Service
public class OccupancyTargetLabelBuilder {

    private static final String SEPARATOR = " • ";

    public String build(OccupancyEntity occupancy) {
        if (occupancy == null || occupancy.getBuilding() == null) {
            return null;
        }

        BuildingEntity building = occupancy.getBuilding();
        PropertyLayoutMode layoutMode = building.getLayoutMode();
        List<String> parts = new ArrayList<>();

        FloorEntity floor = resolveFloor(occupancy);
        UnitEntity unit = occupancy.getUnit();
        RoomEntity room = occupancy.getRoom();

        addSegment(parts, building.getName());
        addSegment(parts, formatFloorLabel(floor));

        switch (layoutMode) {
            case CORRIDOR_PG -> addSleepingSegment(parts, room, occupancy.getBed());
            case APARTMENT_PG -> {
                if (unit != null && !unit.isSynthetic()) {
                    addSegment(parts, unit.getName());
                }
                addSleepingSegment(parts, room, occupancy.getBed());
            }
            case CO_LIVING -> {
                if (unit != null && !unit.isSynthetic()) {
                    addSegment(parts, unit.getName());
                }
                addSleepingSegment(parts, room, occupancy.getBed());
            }
            case RENTAL -> {
                if (unit != null && !unit.isSynthetic()) {
                    addSegment(parts, unit.getName());
                }
            }
            default -> {
                if (unit != null && !unit.isSynthetic()) {
                    addSegment(parts, unit.getName());
                }
                addSleepingSegment(parts, room, occupancy.getBed());
            }
        }

        return parts.isEmpty() ? null : String.join(SEPARATOR, parts);
    }

    private FloorEntity resolveFloor(OccupancyEntity occupancy) {
        if (occupancy.getFloor() != null) {
            return occupancy.getFloor();
        }
        UnitEntity unit = occupancy.getUnit();
        if (unit != null && unit.getFloor() != null) {
            return unit.getFloor();
        }
        RoomEntity room = occupancy.getRoom();
        if (room == null) {
            return null;
        }
        if (room.getFloor() != null) {
            return room.getFloor();
        }
        if (room.getUnit() != null && room.getUnit().getFloor() != null) {
            return room.getUnit().getFloor();
        }
        return null;
    }

    private void addSleepingSegment(List<String> parts, RoomEntity room, BedEntity bed) {
        if (room != null && room.getName() != null) {
            addSegment(parts, formatPrefixed(room.getName(), "Room "));
        }
        if (bed != null) {
            String bedLabel = bed.getBedNumber() != null && !bed.getBedNumber().isBlank()
                    ? bed.getBedNumber()
                    : bed.getName();
            addSegment(parts, formatPrefixed(bedLabel, "Bed "));
        }
    }

    private String formatFloorLabel(FloorEntity floor) {
        if (floor == null) {
            return null;
        }
        Hibernate.initialize(floor);
        if (floor.getName() == null) {
            return null;
        }
        String trimmed = floor.getName().trim();
        if (trimmed.regionMatches(true, 0, "Floor ", 0, 6)) {
            return trimmed;
        }
        return "Floor " + trimmed;
    }

    private String formatPrefixed(String name, String prefix) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return trimmed;
        }
        return prefix + trimmed;
    }

    private void addSegment(List<String> parts, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (!trimmed.isEmpty()) {
            parts.add(trimmed);
        }
    }
}
