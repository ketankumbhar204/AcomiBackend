package com.acomi.acomi_backend.location.application.support;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.location.api.dto.response.LocationRecordResponse;
import com.acomi.acomi_backend.location.application.service.LocationReferenceService;
import com.acomi.acomi_backend.space.application.support.LocationLocalityAliases;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Maps a Geoapify suggestion onto an existing ACOMI reference row.
 * Never creates locations. Distinct pincodes stay distinct: more than one
 * remaining candidate is not a match.
 */
@Component
public class LocationAutocompleteMatcher {

    private static final Logger log = LoggerFactory.getLogger(LocationAutocompleteMatcher.class);
    private static final int CANDIDATE_LIMIT = 20;

    private final LocationReferenceService locationReferenceService;

    public LocationAutocompleteMatcher(LocationReferenceService locationReferenceService) {
        this.locationReferenceService = locationReferenceService;
    }

    public LocationRecordResponse match(
            String area,
            String city,
            String district,
            String state,
            String pincode,
            String contextState,
            String contextDistrict) {
        String name = firstText(area, city);
        if (!StringUtils.hasText(name) && !StringUtils.hasText(pincode)) {
            return null;
        }
        String query = StringUtils.hasText(name) ? name : pincode;
        List<LocationRecordResponse> candidates;
        try {
            candidates = locationReferenceService.search(
                    query, CANDIDATE_LIMIT, contextState, contextDistrict, null);
        } catch (BusinessException ex) {
            log.warn("geoapify_acomi_match_skipped reason=location_reference_unavailable");
            return null;
        }
        List<LocationRecordResponse> reliable = candidates.stream()
                .filter(candidate -> reliable(candidate, area, district, state, pincode))
                .toList();
        if (reliable.size() != 1) {
            return null;
        }
        return reliable.get(0);
    }

    private static boolean reliable(
            LocationRecordResponse candidate, String area, String district, String state, String pincode) {
        if (StringUtils.hasText(area)) {
            if (!sameArea(area, candidate.getLocation())) {
                return false;
            }
        } else if (!StringUtils.hasText(pincode) || !pincode.equals(blankToEmpty(candidate.getPincode()))) {
            return false;
        }
        if (StringUtils.hasText(pincode) && !pincode.equals(blankToEmpty(candidate.getPincode()))) {
            return false;
        }
        if (StringUtils.hasText(state) && !normalize(state).equals(normalize(candidate.getState()))) {
            return false;
        }
        if (StringUtils.hasText(district) && !sameDistrict(district, candidate.getDistrict())) {
            return false;
        }
        return true;
    }

    private static boolean sameArea(String left, String right) {
        String normalizedLeft = normalize(LocationOfficeNameNormalizer.toDisplayName(left));
        String normalizedRight = normalize(LocationOfficeNameNormalizer.toDisplayName(right));
        if (normalizedLeft.equals(normalizedRight)) {
            return true;
        }
        return LocationLocalityAliases.expand(normalizedLeft).contains(normalizedRight);
    }

    private static boolean sameDistrict(String left, String right) {
        return districtKey(left).equals(districtKey(right));
    }

    private static String districtKey(String value) {
        String normalized = normalize(value);
        if (normalized.endsWith(" district")) {
            return normalized.substring(0, normalized.length() - " district".length()).trim();
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
