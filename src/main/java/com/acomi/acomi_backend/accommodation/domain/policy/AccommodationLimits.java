package com.acomi.acomi_backend.accommodation.domain.policy;

public final class AccommodationLimits {

    public static final int MAX_BEDS_PER_SETUP = 500;
    public static final int MAX_ROOMS_PER_BULK = 50;
    public static final int MAX_UNITS_PER_BULK = 50;
    public static final int MAX_FLOORS_PER_SETUP = 20;
    public static final int MAX_UNITS_PER_SETUP = 200;
    public static final int WARNING_BED_THRESHOLD = 400;

    private AccommodationLimits() {}
}
