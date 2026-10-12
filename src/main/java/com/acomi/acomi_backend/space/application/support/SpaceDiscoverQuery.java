package com.acomi.acomi_backend.space.application.support;

import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.math.BigDecimal;
import java.util.List;

/** Filter set applied by discover before pagination. Within a group, values are OR. Groups are AND. */
public record SpaceDiscoverQuery(
        String search,
        String location,
        List<SpaceType> types,
        BigDecimal minRent,
        BigDecimal maxRent,
        List<String> amenityCodes,
        List<GenderPolicy> genders,
        List<SharingOccupancy> sharings,
        List<FoodAvailability> foods,
        List<FurnishingLevel> furnishings,
        boolean contactAvailable,
        boolean servicedApartment,
        List<String> mealTypes,
        List<String> foodTypes) {

    public SpaceDiscoverQuery {
        types = types == null ? List.of() : List.copyOf(types);
        amenityCodes = amenityCodes == null ? List.of() : List.copyOf(amenityCodes);
        genders = genders == null ? List.of() : List.copyOf(genders);
        sharings = sharings == null ? List.of() : List.copyOf(sharings);
        foods = foods == null ? List.of() : List.copyOf(foods);
        furnishings = furnishings == null ? List.of() : List.copyOf(furnishings);
        mealTypes = normalizeCodes(mealTypes);
        foodTypes = normalizeCodes(foodTypes);
    }

    public SpaceDiscoverQuery(
            String search,
            String location,
            List<SpaceType> types,
            BigDecimal minRent,
            BigDecimal maxRent,
            List<String> amenityCodes,
            List<GenderPolicy> genders,
            List<SharingOccupancy> sharings,
            List<FoodAvailability> foods,
            List<FurnishingLevel> furnishings,
            boolean contactAvailable,
            boolean servicedApartment) {
        this(
                search,
                location,
                types,
                minRent,
                maxRent,
                amenityCodes,
                genders,
                sharings,
                foods,
                furnishings,
                contactAvailable,
                servicedApartment,
                List.of(),
                List.of());
    }

    private static List<String> normalizeCodes(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_'))
                .distinct()
                .toList();
    }

    public SpaceDiscoverQuery(
            String search,
            String location,
            List<SpaceType> types,
            BigDecimal minRent,
            BigDecimal maxRent,
            List<String> amenityCodes) {
        this(search, location, types, minRent, maxRent, amenityCodes, List.of(), List.of(), List.of(), List.of(), false, false);
    }

    public static SpaceDiscoverQuery of(String search, SpaceType type, String location) {
        return new SpaceDiscoverQuery(
                search, location, type == null ? List.of() : List.of(type), null, null, List.of());
    }
}
