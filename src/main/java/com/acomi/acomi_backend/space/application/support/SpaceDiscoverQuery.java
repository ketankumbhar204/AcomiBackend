package com.acomi.acomi_backend.space.application.support;

import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.math.BigDecimal;
import java.util.List;

/** Filter set applied by discover before pagination. */
public record SpaceDiscoverQuery(
        String search,
        String location,
        List<SpaceType> types,
        BigDecimal minRent,
        BigDecimal maxRent,
        List<String> amenityCodes) {

    public SpaceDiscoverQuery {
        types = types == null ? List.of() : List.copyOf(types);
        amenityCodes = amenityCodes == null ? List.of() : List.copyOf(amenityCodes);
    }

    public static SpaceDiscoverQuery of(String search, SpaceType type, String location) {
        return new SpaceDiscoverQuery(
                search, location, type == null ? List.of() : List.of(type), null, null, List.of());
    }
}
