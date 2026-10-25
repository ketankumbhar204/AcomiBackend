package com.acomi.acomi_backend.admin.bulkimport.property;

import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Maps Excel space-type text onto {@link SpaceType}.
 *
 * <p>A cell is split only on explicit separators ({@code /}, {@code &}, {@code +}, comma, or the
 * word "and"). Each segment is resolved on its own. Several recognized types stay as candidates
 * for review. One listing stores one type, so a mixed cell is not saved until the reviewer keeps
 * a single type.
 */
public final class SpaceTypeNormalizer {

    public record Result(
            String original,
            SpaceType type,
            GenderPolicy inferredGender,
            boolean normalized,
            String warning,
            String error,
            List<SpaceType> candidates) {

        public Result {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    private static final String CHOOSE_ONE =
            "Choose one primary type. Only that type is saved; other selected types are not stored.";

    private SpaceTypeNormalizer() {}

    public static Result normalize(String raw) {
        String value = PropertyBulkImportValueParser.blankToNull(raw);
        if (value == null) {
            return blank(raw);
        }
        List<String> segments = segments(value);
        if (segments.isEmpty()) {
            return blank(raw);
        }
        if (segments.size() == 1) {
            return resolveSingle(value, segments.get(0));
        }
        return resolveMixed(value, segments);
    }

    public static String unknownMessage(String value) {
        return "Unknown property type: \"" + value + "\"";
    }

    private static Result resolveSingle(String value, String segment) {
        String key = PropertyBulkImportValueParser.classificationKey(segment);
        if (isUnsafe(key)) {
            return invalid(value);
        }
        SegmentHit hit = classify(key);
        if (hit.kind == HitKind.UNKNOWN) {
            return invalid(value);
        }
        if (hit.kind == HitKind.AMBIGUOUS) {
            return review(value, hit.candidates, inferGender(key));
        }
        GenderPolicy inferred = inferGender(key);
        boolean normalized = !BulkImportLabels.spaceType(hit.type).equalsIgnoreCase(value.trim());
        return new Result(value, hit.type, inferred, normalized, null, null, List.of(hit.type));
    }

    private static Result resolveMixed(String value, List<String> segments) {
        Set<SpaceType> candidates = new LinkedHashSet<>();
        boolean ambiguous = false;
        boolean unknownSegment = false;
        StringBuilder genderKey = new StringBuilder();
        for (String segment : segments) {
            String key = PropertyBulkImportValueParser.classificationKey(segment);
            if (!genderKey.isEmpty()) {
                genderKey.append(' ');
            }
            genderKey.append(key);
            if (isFoodStallDescriptor(key)
                    && PropertyBulkImportValueParser.hasCoreMessPhrase(
                            PropertyBulkImportValueParser.classificationKey(value))) {
                continue;
            }
            if (isUnsafe(key)) {
                return invalid(value);
            }
            SegmentHit hit = classify(key);
            if (hit.kind == HitKind.UNKNOWN) {
                unknownSegment = true;
                continue;
            }
            if (hit.kind == HitKind.AMBIGUOUS) {
                ambiguous = true;
            }
            candidates.addAll(hit.candidates);
        }
        if (candidates.isEmpty()) {
            return invalid(value);
        }
        List<SpaceType> ordered = List.copyOf(candidates);
        if (unknownSegment) {
            ambiguous = true;
        }
        if (ordered.size() == 1 && !ambiguous) {
            SpaceType type = ordered.get(0);
            boolean normalized = !BulkImportLabels.spaceType(type).equalsIgnoreCase(value.trim());
            return new Result(value, type, inferGender(genderKey.toString()), normalized, null, null, ordered);
        }
        return review(value, ordered, inferGender(genderKey.toString()));
    }

    private static Result review(String value, List<SpaceType> candidates, GenderPolicy inferred) {
        return new Result(
                value,
                null,
                inferred,
                false,
                null,
                CHOOSE_ONE + " Detected: " + labels(candidates) + ".",
                candidates);
    }

    private static String labels(List<SpaceType> candidates) {
        StringBuilder labels = new StringBuilder();
        for (SpaceType type : candidates) {
            if (!labels.isEmpty()) {
                labels.append(", ");
            }
            labels.append(BulkImportLabels.spaceType(type));
        }
        return labels.toString();
    }

    private static Result blank(String raw) {
        return new Result(raw, null, null, false, null, null, List.of());
    }

    private static Result invalid(String value) {
        return new Result(value, null, null, false, null, unknownMessage(value), List.of());
    }

    private static List<String> segments(String value) {
        String[] parts = value.split("\\s*(?:/|&|\\+|\\band\\b|,)\\s*", -1);
        List<String> segments = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                segments.add(trimmed);
            }
        }
        return segments;
    }

    private enum HitKind {
        TYPE,
        AMBIGUOUS,
        UNKNOWN
    }

    private record SegmentHit(HitKind kind, SpaceType type, List<SpaceType> candidates) {
        private static SegmentHit type(SpaceType type) {
            return new SegmentHit(HitKind.TYPE, type, List.of(type));
        }

        private static SegmentHit ambiguous(SpaceType... types) {
            return new SegmentHit(HitKind.AMBIGUOUS, null, List.of(types));
        }

        private static SegmentHit unknown() {
            return new SegmentHit(HitKind.UNKNOWN, null, List.of());
        }
    }

    private static SegmentHit classify(String key) {
        if (isExactCorporateStay(key)) {
            return SegmentHit.type(SpaceType.CORPORATE_STAY);
        }
        if (isAmbiguousCorporate(key)) {
            return SegmentHit.ambiguous(SpaceType.CORPORATE_STAY, SpaceType.RENTAL);
        }
        SpaceType type = resolveKnown(key);
        return type == null ? SegmentHit.unknown() : SegmentHit.type(type);
    }

    private static boolean isExactCorporateStay(String key) {
        return key.equals("corporate stay")
                || key.equals("corporate stays")
                || key.equals("corporate accommodation")
                || key.equals("corporate housing");
    }

    private static boolean isAmbiguousCorporate(String key) {
        boolean corporate = PropertyBulkImportValueParser.hasWord(key, "corporate")
                || PropertyBulkImportValueParser.hasWord(key, "company");
        if (!corporate) {
            return false;
        }
        return PropertyBulkImportValueParser.hasPhrase(
                        key, "apartment", "apartments", "serviced", "rental", "guest house", "guesthouse")
                || PropertyBulkImportValueParser.hasWord(key, "flat")
                || PropertyBulkImportValueParser.hasWord(key, "flats");
    }

    private static SpaceType resolveKnown(String key) {
        if (PropertyBulkImportValueParser.hasPhrase(key, "co living", "coliving", "co live", "colive")) {
            return SpaceType.CO_LIVING;
        }
        if (PropertyBulkImportValueParser.hasCoreMessPhrase(key)
                || ((PropertyBulkImportValueParser.hasCateringFoodServicePhrase(key)
                                || PropertyBulkImportValueParser.hasWord(key, "meals")
                                || PropertyBulkImportValueParser.hasWord(key, "meal"))
                        && !PropertyBulkImportValueParser.hasExplicitLodging(key))) {
            return SpaceType.MESS;
        }
        if (PropertyBulkImportValueParser.hasPhrase(
                        key, "serviced", "rental", "rentals", "apartment", "apartments", "flat", "flats")
                || PropertyBulkImportValueParser.hasWord(key, "rent")) {
            return SpaceType.RENTAL;
        }
        boolean pg = PropertyBulkImportValueParser.hasWord(key, "pg") || key.contains("paying guest");
        boolean hostel = PropertyBulkImportValueParser.hasWord(key, "hostel")
                || PropertyBulkImportValueParser.hasWord(key, "hostels");
        if (pg) {
            return SpaceType.PG;
        }
        if (hostel) {
            return SpaceType.HOSTEL;
        }
        if (PropertyBulkImportValueParser.isGuestHouseFallback(key)
                && !PropertyBulkImportValueParser.hasWord(key, "corporate")
                && !PropertyBulkImportValueParser.hasWord(key, "company")) {
            return SpaceType.RENTAL;
        }
        return null;
    }

    private static boolean isFoodStallDescriptor(String key) {
        return PropertyBulkImportValueParser.hasPhrase(key, "curry point", "food court", "restaurant")
                && !PropertyBulkImportValueParser.hasCoreMessPhrase(key)
                && !PropertyBulkImportValueParser.hasExplicitLodging(key);
    }

    private static boolean isUnsafe(String key) {
        boolean namedMessOrLodging = PropertyBulkImportValueParser.hasCoreMessPhrase(key)
                || PropertyBulkImportValueParser.hasExplicitLodging(key)
                || isExactCorporateStay(key);
        if (!namedMessOrLodging
                && PropertyBulkImportValueParser.hasPhrase(
                        key, "food court", "curry point", "restaurant", "apartment complex", "commercial building", "pg owner")) {
            return true;
        }
        if (PropertyBulkImportValueParser.hasPhrase(key, "apartment complex", "commercial building", "pg owner")) {
            return true;
        }
        if (PropertyBulkImportValueParser.hasWord(key, "hotel")
                && !PropertyBulkImportValueParser.hasWord(key, "hostel")
                && !PropertyBulkImportValueParser.hasWord(key, "pg")) {
            return true;
        }
        if ((PropertyBulkImportValueParser.hasWord(key, "office")
                        || PropertyBulkImportValueParser.hasWord(key, "shop"))
                && !PropertyBulkImportValueParser.hasExplicitLodging(key)
                && !PropertyBulkImportValueParser.hasCoreMessPhrase(key)
                && !isExactCorporateStay(key)) {
            return true;
        }
        return key.equals("accommodation")
                || key.equals("property")
                || key.equals("room")
                || key.equals("rooms");
    }

    private static GenderPolicy inferGender(String key) {
        boolean male = hasAny(key, "gent", "gents", "man", "men", "mens", "boy", "boys", "male");
        boolean female = hasAny(key, "lady", "ladies", "woman", "women", "womens", "girl", "girls", "female");
        if (male && female) {
            return null;
        }
        if (male) {
            return GenderPolicy.MALE;
        }
        if (female) {
            return GenderPolicy.FEMALE;
        }
        return null;
    }

    private static boolean hasAny(String key, String... words) {
        for (String word : words) {
            if (PropertyBulkImportValueParser.hasWord(key, word)) {
                return true;
            }
        }
        return false;
    }
}
