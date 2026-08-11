package com.amico.amico_backend.accommodation.domain.model;

public enum AccommodationDeletionRoot {
    BUILDING("Building"),
    FLOOR("Floor"),
    UNIT("Unit"),
    ROOM("Room"),
    BED("Bed");

    private final String label;

    AccommodationDeletionRoot(String label) {
        this.label = label;
    }

    public String formatLabel(String name) {
        return label + " " + name;
    }
}
