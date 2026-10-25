package com.acomi.acomi_backend.property.domain.model;

import com.acomi.acomi_backend.space.domain.model.SpaceType;

/**
 * What the quoted starting price is measured against.
 * {@link #forPropertyType(SpaceType)} is the default. A listing may store a different supported value.
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
            case CORPORATE_STAY -> throw new IllegalArgumentException(
                    "Corporate Stay has no default price basis. Submit PER_BED, PER_ROOM, or PER_UNIT explicitly.");
        };
    }

    /** Uses the submitted unit when present, otherwise the type default. */
    public static PriceBasis resolve(SpaceType propertyType, PriceBasis requested) {
        return requested != null ? requested : forPropertyType(propertyType);
    }
}
