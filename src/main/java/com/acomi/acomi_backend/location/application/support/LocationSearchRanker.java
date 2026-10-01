package com.acomi.acomi_backend.location.application.support;

import com.acomi.acomi_backend.location.domain.model.LocationRecord;
import com.acomi.acomi_backend.space.application.support.LocationLocalityAliases;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tokenizes location search queries and scores records. Higher scores rank first.
 * Context parameters boost ranking and never exclude a match.
 */
public final class LocationSearchRanker {

    static final int EXACT_LOCATION = 100;
    static final int LOCATION_PREFIX = 40;
    static final int LOCATION_CONTAINS = 20;
    static final int QUERY_DISTRICT = 16;
    static final int QUERY_TALUK = 14;
    static final int QUERY_STATE = 12;
    static final int PINCODE_EXACT = 10;
    static final int PINCODE_PREFIX = 8;
    static final int OFFICE_CONTAINS = 6;
    static final int CONTEXT_DISTRICT = 90;
    static final int CONTEXT_STATE = 30;
    static final int CONTEXT_TALUK = 20;
    private static final int MIN_KEYWORD_LENGTH = 2;

    private LocationSearchRanker() {}

    public static List<String> keywords(String query) {
        if (query == null) {
            return List.of();
        }
        String trimmed = query.trim().toLowerCase(Locale.ROOT);
        if (trimmed.length() < MIN_KEYWORD_LENGTH) {
            return List.of();
        }
        String[] parts = trimmed.split("[,\\s]+");
        List<String> keywords = new ArrayList<>();
        for (String part : parts) {
            if (part.length() >= MIN_KEYWORD_LENGTH) {
                keywords.add(part);
            }
        }
        return List.copyOf(keywords);
    }

    public static String identity(LocationRecord record) {
        return String.join(
                "|",
                nullToEmpty(record.location()),
                nullToEmpty(record.pincode()),
                nullToEmpty(record.district()),
                nullToEmpty(record.state()),
                nullToEmpty(record.cityTaluka()));
    }

    /**
     * @return score when every keyword matches the record, otherwise {@code -1}
     */
    public static int score(
            LocationRecord record, List<String> keywords, String contextState, String contextDistrict, String contextTaluk) {
        if (keywords == null || keywords.isEmpty()) {
            return -1;
        }
        Fields fields = Fields.of(record);
        int total = 0;
        for (String keyword : keywords) {
            int keywordScore = scoreKeyword(fields, keyword);
            if (keywordScore < 0) {
                return -1;
            }
            total += keywordScore;
        }
        return total + contextBoost(fields, contextState, contextDistrict, contextTaluk);
    }

    private static int scoreKeyword(Fields fields, String keyword) {
        int best = -1;
        for (String alias : LocationLocalityAliases.expand(keyword)) {
            best = Math.max(best, scoreAlias(fields, alias));
        }
        return best;
    }

    private static int scoreAlias(Fields fields, String alias) {
        if (fields.location.equals(alias)) {
            return EXACT_LOCATION;
        }
        if (!fields.location.isEmpty() && fields.location.startsWith(alias)) {
            return LOCATION_PREFIX;
        }
        if (fields.location.contains(alias)) {
            return LOCATION_CONTAINS;
        }
        if (fields.pincode.equals(alias)) {
            return PINCODE_EXACT;
        }
        if (fields.district.equals(alias)) {
            return QUERY_DISTRICT;
        }
        if (fields.taluk.equals(alias)) {
            return QUERY_TALUK;
        }
        if (fields.state.equals(alias) || fields.stateKey.equals(LocationIndex.key(alias))) {
            return QUERY_STATE;
        }
        if (!fields.pincode.isEmpty() && fields.pincode.startsWith(alias)) {
            return PINCODE_PREFIX;
        }
        if (fields.district.contains(alias) || fields.taluk.contains(alias) || fields.state.contains(alias)) {
            return QUERY_DISTRICT;
        }
        if (fields.office.contains(alias)) {
            return OFFICE_CONTAINS;
        }
        return -1;
    }

    private static int contextBoost(Fields fields, String contextState, String contextDistrict, String contextTaluk) {
        int boost = 0;
        String stateKey = LocationIndex.key(contextState);
        String districtKey = LocationIndex.key(contextDistrict);
        String talukKey = LocationIndex.key(contextTaluk);
        if (!stateKey.isEmpty() && stateKey.equals(fields.stateKey)) {
            boost += CONTEXT_STATE;
        }
        if (!districtKey.isEmpty() && districtKey.equals(fields.districtKey)) {
            boost += CONTEXT_DISTRICT;
        }
        if (!talukKey.isEmpty() && talukKey.equals(fields.talukKey)) {
            boost += CONTEXT_TALUK;
        }
        return boost;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record Fields(
            String location,
            String office,
            String pincode,
            String taluk,
            String district,
            String state,
            String stateKey,
            String districtKey,
            String talukKey) {

        static Fields of(LocationRecord record) {
            return new Fields(
                    lower(record.location()),
                    lower(record.officeName()),
                    lower(record.pincode()),
                    lower(record.cityTaluka()),
                    lower(record.district()),
                    lower(record.state()),
                    LocationIndex.key(record.state()),
                    LocationIndex.key(record.district()),
                    LocationIndex.key(record.cityTaluka()));
        }

        private static String lower(String value) {
            return value == null ? "" : value.toLowerCase(Locale.ROOT);
        }
    }
}
