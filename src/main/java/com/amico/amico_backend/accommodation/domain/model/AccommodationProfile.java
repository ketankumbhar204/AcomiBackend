package com.amico.amico_backend.accommodation.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AccommodationProfile {

    private final boolean floorsAllowed;
    private final boolean unitsAllowed;
    private final boolean roomsUnderFloorAllowed;
    private final boolean roomsUnderUnitAllowed;
    private final boolean bedsAllowed;

    public static AccommodationProfile forPgOrHostel() {
        return new AccommodationProfile(true, false, true, false, true);
    }

    public static AccommodationProfile forCoLiving() {
        return new AccommodationProfile(false, true, false, true, true);
    }

    public static AccommodationProfile forRental() {
        return new AccommodationProfile(false, true, false, false, false);
    }
}
