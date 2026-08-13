package com.acomi.acomi_backend.occupancy.application.service;

import com.acomi.acomi_backend.accommodation.domain.model.PropertyLayoutMode;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.FloorEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.UnitEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.BuildingRepository;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Builds human-readable accommodation trails for payment cards and similar summaries.
 * Mirrors the visible hierarchy from accommodation UI profiles (layout-mode aware).
 */
@Service
@RequiredArgsConstructor
public class OccupancyTargetLabelBuilder {

    private static final String SEPARATOR = " • ";

    private final BuildingRepository buildingRepository;

    public String build(OccupancyEntity occupancy) {
        if (occupancy == null || occupancy.getBuilding() == null) {
            return null;
        }

        BuildingEntity building = occupancy.getBuilding();
        PropertyLayoutMode layoutMode = building.getLayoutMode();
        boolean singleBuilding = !shouldShowBuilding(occupancy.getSpace(), building);
        List<String> parts = new ArrayList<>();

        if (!singleBuilding) {
            addSegment(parts, building.getName());
        }

        FloorEntity floor = resolveFloor(occupancy);
        UnitEntity unit = occupancy.getUnit();
        RoomEntity room = occupancy.getRoom();

        switch (layoutMode) {
            case CORRIDOR_PG -> {
                addSegment(parts, formatFloorLabel(floor));
                addSleepingSegment(parts, room, occupancy.getBed());
            }
            case APARTMENT_PG -> {
                if (!singleBuilding) {
                    addSegment(parts, formatFloorLabel(floor));
                }
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
                addSegment(parts, formatFloorLabel(floor));
                if (unit != null && !unit.isSynthetic()) {
                    addSegment(parts, unit.getName());
                }
                addSleepingSegment(parts, room, occupancy.getBed());
            }
        }

        return parts.isEmpty() ? null : String.join(SEPARATOR, parts);
    }

    private boolean shouldShowBuilding(SpaceEntity space, BuildingEntity building) {
        if (space == null || building == null) {
            return false;
        }
        return buildingRepository.findActiveBySpaceId(space.getId()).size() > 1;
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
        if (bed != null && bed.getName() != null) {
            addSegment(parts, formatRoomLabel(bed.getName()));
            return;
        }
        if (room != null && room.getName() != null) {
            addSegment(parts, formatRoomLabel(room.getName()));
        }
    }

    private String formatFloorLabel(FloorEntity floor) {
        if (floor == null || floor.getName() == null) {
            return null;
        }
        String trimmed = floor.getName().trim();
        if (trimmed.regionMatches(true, 0, "Floor ", 0, 6)) {
            return trimmed;
        }
        return "Floor " + trimmed;
    }

    private String formatRoomLabel(String name) {
        String trimmed = name.trim();
        if (trimmed.regionMatches(true, 0, "Room ", 0, 5)) {
            return trimmed;
        }
        return "Room " + trimmed;
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
