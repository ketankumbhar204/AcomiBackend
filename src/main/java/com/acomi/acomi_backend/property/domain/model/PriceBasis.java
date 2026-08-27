package com.acomi.acomi_backend.property.domain.model;

import com.acomi.acomi_backend.space.domain.model.SpaceType;

/**
 * What the quoted starting price is measured against. Always derived from the property type
 * so the browser cannot choose a basis that contradicts the accommodation model.
 */
public enum PriceBasis {
    PER_BED,
    PER_ROOM,
    PER_UNIT;

    /** Mirrors AccommodationProfileResolver: PG/Hostel allocate beds, co-living rooms, rentals whole units. */
    public static PriceBasis forPropertyType(SpaceType propertyType) {
        return switch (propertyType) {
            case PG, HOSTEL -> PER_BED;
            case CO_LIVING -> PER_ROOM;
            case RENTAL, MESS -> PER_UNIT;
        };
    }
}
