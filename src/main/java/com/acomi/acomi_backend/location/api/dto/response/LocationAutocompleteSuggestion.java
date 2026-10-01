package com.acomi.acomi_backend.location.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * Normalized autocomplete suggestion. Geoapify is the suggestion source.
 * {@code acomiLocation} is set only when an existing reference row matches reliably.
 */
@Getter
@Builder
@Schema(description = "Location autocomplete suggestion. Not an ACOMI listing.")
public class LocationAutocompleteSuggestion {

    public static final String SOURCE_GEOAPIFY = "GEOAPIFY";

    private String id;
    private String displayName;
    private String formattedAddress;
    private String name;
    private String area;
    private String city;
    private String taluka;
    private String district;
    private String state;
    private String pincode;
    private Double latitude;
    private Double longitude;
    private String country;
    private String source;
    private boolean matched;
    private LocationRecordResponse acomiLocation;
}
