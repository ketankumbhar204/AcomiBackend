package com.acomi.acomi_backend.location.application.support;

import com.acomi.acomi_backend.location.domain.model.LocationRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * In-memory indexes over the reference dataset. Lookups do not rescan every row.
 */
public final class LocationIndex {

    private static final Comparator<String> LABEL_ORDER =
            Comparator.comparing(value -> value.toLowerCase(Locale.ROOT));

    private final List<LocationRecord> records;
    private final List<String> states;
    private final Map<String, List<String>> districtsByState;
    private final Map<String, List<String>> talukasByStateDistrict;
    private final Map<String, List<LocationRecord>> areasByStateDistrictTaluk;

    private LocationIndex(
            List<LocationRecord> records,
            List<String> states,
            Map<String, List<String>> districtsByState,
            Map<String, List<String>> talukasByStateDistrict,
            Map<String, List<LocationRecord>> areasByStateDistrictTaluk) {
        this.records = records;
        this.states = states;
        this.districtsByState = districtsByState;
        this.talukasByStateDistrict = talukasByStateDistrict;
        this.areasByStateDistrictTaluk = areasByStateDistrictTaluk;
    }

    public static LocationIndex build(List<LocationRecord> source) {
        List<LocationRecord> records = List.copyOf(source);
        Map<String, String> stateLabels = new TreeMap<>();
        Map<String, Map<String, String>> districtLabels = new LinkedHashMap<>();
        Map<String, Map<String, String>> talukaLabels = new LinkedHashMap<>();
        Map<String, List<LocationRecord>> areas = new LinkedHashMap<>();

        for (LocationRecord record : records) {
            String stateKey = key(record.state());
            String districtKey = key(record.district());
            String talukKey = key(record.cityTaluka());
            stateLabels.putIfAbsent(stateKey, record.state());
            districtLabels
                    .computeIfAbsent(stateKey, ignored -> new TreeMap<>())
                    .putIfAbsent(districtKey, record.district());
            String stateDistrict = composite(stateKey, districtKey);
            talukaLabels
                    .computeIfAbsent(stateDistrict, ignored -> new TreeMap<>())
                    .putIfAbsent(talukKey, record.cityTaluka());
            areas.computeIfAbsent(composite(stateKey, districtKey, talukKey), ignored -> new ArrayList<>())
                    .add(record);
        }

        List<String> states = stateLabels.values().stream().sorted(LABEL_ORDER).toList();
        Map<String, List<String>> districtsByState = new LinkedHashMap<>();
        districtLabels.forEach((stateKey, labels) -> districtsByState.put(
                stateKey, labels.values().stream().sorted(LABEL_ORDER).toList()));
        Map<String, List<String>> talukasByStateDistrict = new LinkedHashMap<>();
        talukaLabels.forEach((compositeKey, labels) -> talukasByStateDistrict.put(
                compositeKey, labels.values().stream().sorted(LABEL_ORDER).toList()));
        areas.replaceAll((ignored, list) -> List.copyOf(list));
        return new LocationIndex(
                records, states, Map.copyOf(districtsByState), Map.copyOf(talukasByStateDistrict), Map.copyOf(areas));
    }

    public List<String> states() {
        return states;
    }

    public List<String> districts(String state) {
        return districtsByState.getOrDefault(key(state), List.of());
    }

    public List<String> talukas(String state, String district) {
        return talukasByStateDistrict.getOrDefault(composite(key(state), key(district)), List.of());
    }

    public List<LocationRecord> areas(String state, String district, String taluk) {
        return areasByStateDistrictTaluk.getOrDefault(
                composite(key(state), key(district), key(taluk)), List.of());
    }

    public List<LocationRecord> search(String query, int limit) {
        return search(query, limit, null, null, null);
    }

    public List<LocationRecord> search(
            String query, int limit, String state, String district, String taluk) {
        List<String> keywords = LocationSearchRanker.keywords(query);
        if (keywords.isEmpty() || limit <= 0) {
            return List.of();
        }
        Map<String, Scored> unique = new LinkedHashMap<>();
        for (LocationRecord record : records) {
            int score = LocationSearchRanker.score(record, keywords, state, district, taluk);
            if (score < 0) {
                continue;
            }
            String identity = LocationSearchRanker.identity(record);
            Scored previous = unique.get(identity);
            if (previous == null || score > previous.score()) {
                unique.put(identity, new Scored(score, record));
            }
        }
        List<Scored> matches = new ArrayList<>(unique.values());
        matches.sort(Comparator.comparingInt(Scored::score)
                .reversed()
                .thenComparing(item -> item.record().location(), LABEL_ORDER)
                .thenComparing(item -> item.record().pincode())
                .thenComparing(item -> item.record().district(), LABEL_ORDER)
                .thenComparing(item -> item.record().state(), LABEL_ORDER)
                .thenComparing(item -> item.record().cityTaluka(), LABEL_ORDER));
        int end = Math.min(limit, matches.size());
        List<LocationRecord> result = new ArrayList<>(end);
        for (int i = 0; i < end; i++) {
            result.add(matches.get(i).record());
        }
        return Collections.unmodifiableList(result);
    }

    public int size() {
        return records.size();
    }

    static String key(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String composite(String... parts) {
        return String.join("\u0000", parts);
    }

    private record Scored(int score, LocationRecord record) {}
}
