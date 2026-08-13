package com.acomi.acomi_backend.accommodation.application.service;

import com.acomi.acomi_backend.accommodation.infrastructure.persistence.projection.AllocationTargetSearchRow;
import com.acomi.acomi_backend.occupancy.domain.model.AllocationTargetType;

public final class AccommodationDisplayPathBuilder {

    private static final String SEPARATOR = " · ";

    private AccommodationDisplayPathBuilder() {}

    public static String buildDisplayPath(AllocationTargetSearchRow row) {
        StringBuilder path = new StringBuilder();
        appendSegment(path, row.getBuildingName());
        if (row.getTargetType() == AllocationTargetType.UNIT) {
            appendSegment(path, row.getUnitName());
            return path.toString();
        }
        if (row.getFloorName() != null && !row.getFloorName().isBlank()) {
            appendSegment(path, row.getFloorName());
        }
        if (row.getUnitName() != null
                && !row.getUnitName().isBlank()
                && row.getFloorName() == null) {
            appendSegment(path, row.getUnitName());
        }
        appendSegment(path, row.getRoomName());
        appendSegment(path, row.getBedName());
        return path.toString();
    }

    public static String buildDisplayPathShort(AllocationTargetSearchRow row) {
        if (row.getTargetType() == AllocationTargetType.UNIT) {
            return firstNonBlank(row.getBuildingCode(), abbreviate(row.getBuildingName()))
                    + "-U"
                    + firstNonBlank(row.getUnitNumber(), row.getUnitName());
        }
        String building = firstNonBlank(row.getBuildingCode(), abbreviate(row.getBuildingName()));
        String floor = row.getFloorName() != null ? abbreviate(row.getFloorName()) : null;
        String room = firstNonBlank(row.getRoomNumber(), abbreviate(row.getRoomName()));
        String bed = firstNonBlank(row.getBedNumber(), abbreviate(row.getBedName()));
        StringBuilder shortPath = new StringBuilder(building);
        if (floor != null) {
            shortPath.append("-").append(floor);
        }
        if (room != null) {
            shortPath.append("-").append(room);
        }
        if (bed != null) {
            shortPath.append("-").append(bed);
        }
        return shortPath.toString();
    }

    private static void appendSegment(StringBuilder path, String segment) {
        if (segment == null || segment.isBlank()) {
            return;
        }
        if (!path.isEmpty()) {
            path.append(SEPARATOR);
        }
        path.append(segment);
    }

    private static String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.length() > 12 ? value.substring(0, 12) : value;
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback != null ? fallback : "";
    }
}
