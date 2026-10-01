package com.acomi.acomi_backend.location.application.service;

import com.acomi.acomi_backend.location.api.dto.response.LocationAutocompleteSuggestion;
import com.acomi.acomi_backend.location.api.dto.response.LocationRecordResponse;
import com.acomi.acomi_backend.location.application.support.LocationAutocompleteMatcher;
import com.acomi.acomi_backend.location.application.support.LocationOfficeNameNormalizer;
import com.acomi.acomi_backend.location.config.GeoapifyProperties;
import com.acomi.acomi_backend.location.infrastructure.geoapify.GeoapifyAutocompleteClient;
import com.acomi.acomi_backend.location.infrastructure.geoapify.GeoapifyAutocompleteClient.ProviderResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LocationAutocompleteService {

    private static final Logger log = LoggerFactory.getLogger(LocationAutocompleteService.class);
    private static final double INDIA_MIN_LAT = 6.0;
    private static final double INDIA_MAX_LAT = 37.5;
    private static final double INDIA_MIN_LON = 68.0;
    private static final double INDIA_MAX_LON = 97.5;

    private final GeoapifyProperties properties;
    private final GeoapifyAutocompleteClient client;
    private final LocationAutocompleteMatcher matcher;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public LocationAutocompleteService(
            GeoapifyProperties properties,
            GeoapifyAutocompleteClient client,
            LocationAutocompleteMatcher matcher) {
        this.properties = properties;
        this.client = client;
        this.matcher = matcher;
    }

    public List<LocationAutocompleteSuggestion> autocomplete(String query, String contextState, String contextDistrict) {
        String text = query == null ? "" : query.trim();
        int minimum = Math.max(1, properties.getMinQueryLength());
        if (text.length() < minimum) {
            return List.of();
        }
        if (!StringUtils.hasText(properties.getApiKey())) {
            log.info("geoapify_autocomplete_disabled reason=missing_api_key queryLength={}", text.length());
            return List.of();
        }
        String cacheKey = text.toLowerCase(Locale.ROOT);
        CacheEntry cached = cache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtMillis() > now) {
            return applyContext(cached.suggestions(), contextState, contextDistrict);
        }
        ProviderResult result = client.autocomplete(text);
        if (!result.available()) {
            return List.of();
        }
        List<LocationAutocompleteSuggestion> suggestions = mapPlaces(result.places(), contextState, contextDistrict);
        remember(cacheKey, suggestions);
        return suggestions;
    }

    private List<LocationAutocompleteSuggestion> applyContext(
            List<LocationAutocompleteSuggestion> cached, String contextState, String contextDistrict) {
        if (!StringUtils.hasText(contextState) && !StringUtils.hasText(contextDistrict)) {
            return cached;
        }
        List<LocationAutocompleteSuggestion> refreshed = new ArrayList<>();
        for (LocationAutocompleteSuggestion suggestion : cached) {
            refreshed.add(withMatch(suggestion, contextState, contextDistrict));
        }
        return List.copyOf(refreshed);
    }

    private void remember(String cacheKey, List<LocationAutocompleteSuggestion> suggestions) {
        int max = Math.max(1, properties.getCacheMaxEntries());
        if (cache.size() >= max) {
            cache.clear();
        }
        long ttlMillis = Math.max(1, properties.getCacheTtlSeconds()) * 1000L;
        cache.put(cacheKey, new CacheEntry(System.currentTimeMillis() + ttlMillis, suggestions));
    }

    private List<LocationAutocompleteSuggestion> mapPlaces(
            List<JsonNode> places, String contextState, String contextDistrict) {
        int limit = Math.min(8, Math.max(1, properties.getLimit()));
        String expectedCountry = expectedCountryCode();
        List<LocationAutocompleteSuggestion> suggestions = new ArrayList<>();
        for (JsonNode place : places) {
            if (suggestions.size() >= limit) {
                break;
            }
            LocationAutocompleteSuggestion suggestion = mapPlace(place, expectedCountry, contextState, contextDistrict);
            if (suggestion != null) {
                suggestions.add(suggestion);
            }
        }
        return List.copyOf(suggestions);
    }

    private LocationAutocompleteSuggestion mapPlace(
            JsonNode place, String expectedCountry, String contextState, String contextDistrict) {
        if (place == null || !place.isObject()) {
            return null;
        }
        if (!inCountry(place, expectedCountry) || isPointOfInterest(place)) {
            return null;
        }
        String name = text(place, "name");
        String area = firstText(
                text(place, "suburb"),
                text(place, "neighbourhood"),
                text(place, "district"),
                distinctFrom(name, text(place, "city")));
        String city = text(place, "city");
        String county = text(place, "county");
        String district = districtName(text(place, "state_district"), county);
        String state = text(place, "state");
        String pincode = pincode(text(place, "postcode"));
        String formatted = text(place, "formatted");
        String displayName = firstText(name, area, city, formatted);
        if (!StringUtils.hasText(displayName)) {
            return null;
        }
        Double latitude = coordinate(place, "lat", INDIA_MIN_LAT, INDIA_MAX_LAT);
        Double longitude = coordinate(place, "lon", INDIA_MIN_LON, INDIA_MAX_LON);
        LocationAutocompleteSuggestion base = LocationAutocompleteSuggestion.builder()
                .id(text(place, "place_id"))
                .displayName(displayName)
                .formattedAddress(formatted)
                .name(name)
                .area(area)
                .city(city)
                .taluka(talukaName(county))
                .district(district)
                .state(state)
                .pincode(pincode)
                .latitude(latitude)
                .longitude(longitude)
                .country(text(place, "country"))
                .source(LocationAutocompleteSuggestion.SOURCE_GEOAPIFY)
                .matched(false)
                .build();
        return withMatch(base, contextState, contextDistrict);
    }

    private LocationAutocompleteSuggestion withMatch(
            LocationAutocompleteSuggestion suggestion, String contextState, String contextDistrict) {
        LocationRecordResponse matched = matcher.match(
                suggestion.getArea(),
                suggestion.getCity(),
                suggestion.getDistrict(),
                suggestion.getState(),
                suggestion.getPincode(),
                contextState,
                contextDistrict);
        if (matched == null) {
            return suggestion;
        }
        return LocationAutocompleteSuggestion.builder()
                .id(suggestion.getId())
                .displayName(suggestion.getDisplayName())
                .formattedAddress(suggestion.getFormattedAddress())
                .name(suggestion.getName())
                .area(matched.getLocation())
                .city(suggestion.getCity())
                .taluka(matched.getCityTaluka())
                .district(matched.getDistrict())
                .state(matched.getState())
                .pincode(matched.getPincode())
                .latitude(suggestion.getLatitude())
                .longitude(suggestion.getLongitude())
                .country(suggestion.getCountry())
                .source(suggestion.getSource())
                .matched(true)
                .acomiLocation(matched)
                .build();
    }

    private String expectedCountryCode() {
        String code = properties.getCountryCode();
        return StringUtils.hasText(code) ? code.trim().toLowerCase(Locale.ROOT) : "in";
    }

    private static String districtName(String stateDistrict, String county) {
        if (isAdministrativeDistrict(stateDistrict)) {
            return stateDistrict;
        }
        if (isAdministrativeDistrict(county)) {
            return county;
        }
        return null;
    }

    private static boolean isAdministrativeDistrict(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return !normalized.contains("subdistrict") && !normalized.contains("taluka") && !normalized.contains("taluk");
    }

    private static String talukaName(String county) {
        if (!StringUtils.hasText(county)) {
            return null;
        }
        String trimmed = county.trim();
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (normalized.contains("subdistrict") || normalized.contains("taluka") || normalized.contains("taluk")) {
            return trimmed.replaceAll("(?i)\\s+(subdistrict|taluka|taluk)$", "").trim();
        }
        return null;
    }

    private static boolean isPointOfInterest(JsonNode place) {
        String type = text(place, "result_type");
        if (!StringUtils.hasText(type)) {
            return false;
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("amenity") || normalized.equals("building");
    }

    private static boolean inCountry(JsonNode place, String expectedCountry) {
        String code = text(place, "country_code");
        if (StringUtils.hasText(code)) {
            return expectedCountry.equals(code.trim().toLowerCase(Locale.ROOT));
        }
        String country = text(place, "country");
        if (!StringUtils.hasText(country)) {
            return true;
        }
        return "india".equals(country.trim().toLowerCase(Locale.ROOT));
    }

    private static String pincode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() != 6 || digits.charAt(0) == '0') {
            return null;
        }
        return digits;
    }

    private static Double coordinate(JsonNode place, String field, double min, double max) {
        JsonNode node = place.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        double value;
        if (node.isNumber()) {
            value = node.doubleValue();
        } else if (node.isTextual()) {
            try {
                value = Double.parseDouble(node.asText().trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        } else {
            return null;
        }
        if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) {
            return null;
        }
        return value;
    }

    private static String text(JsonNode place, String field) {
        JsonNode node = place.get(field);
        if (node == null || node.isNull() || !node.isValueNode()) {
            return null;
        }
        String value = node.asText().trim();
        if (!StringUtils.hasText(value) || "null".equalsIgnoreCase(value)) {
            return null;
        }
        return LocationOfficeNameNormalizer.toDisplayName(value);
    }

    private static String distinctFrom(String name, String city) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        if (StringUtils.hasText(city) && name.equalsIgnoreCase(city.trim())) {
            return null;
        }
        return name;
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private record CacheEntry(long expiresAtMillis, List<LocationAutocompleteSuggestion> suggestions) {}
}
