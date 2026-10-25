package com.acomi.acomi_backend.admin.bulkimport.property;

import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Maps Excel space-type text onto one {@link SpaceType}.
 *
 * <p>A cell is split only on explicit separators ({@code /}, {@code &}, {@code +}, comma, or the
 * word "and"). Each segment is resolved from known aliases. When several canonical types are
 * recognized, {@link #PRIORITY} picks the one that is stored. The others stay on the result as
 * candidates and the cell is a warning, not an error. A segment that is not a known alias keeps
 * the cell invalid.
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

    /** Highest priority first. The first match is the type that is saved. */
    private static final List<SpaceType> PRIORITY = List.of(
            SpaceType.PG,
            SpaceType.CO_LIVING,
            SpaceType.HOSTEL,
            SpaceType.CORPORATE_STAY,
            SpaceType.RENTAL,
            SpaceType.MESS);

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
        String key = typeKey(segment);
        if (isUnsafe(key)) {
            return invalid(value);
        }
        List<SpaceType> found = typesIn(key);
        if (found.isEmpty()) {
            return invalid(value);
        }
        return finish(value, found, inferGender(key));
    }

    private static Result resolveMixed(String value, List<String> segments) {
        Set<SpaceType> candidates = new LinkedHashSet<>();
        boolean unknownSegment = false;
        StringBuilder genderKey = new StringBuilder();
        String wholeKey = typeKey(value);
        for (String segment : segments) {
            String key = typeKey(segment);
            if (!genderKey.isEmpty()) {
                genderKey.append(' ');
            }
            genderKey.append(key);
            if (isIgnorableDescriptor(key, wholeKey) || isGenderOnly(key)) {
                continue;
            }
            if (isHotelBesideServicedCorporate(key, wholeKey)) {
                continue;
            }
            if (isUnsafe(key)) {
                return invalid(value);
            }
            List<SpaceType> found = typesIn(key);
            if (found.isEmpty()) {
                unknownSegment = true;
                continue;
            }
            candidates.addAll(found);
        }
        if (candidates.isEmpty() || unknownSegment) {
            return invalid(value);
        }
        return finish(value, List.copyOf(candidates), inferGender(genderKey.toString()));
    }

    private static Result finish(String value, List<SpaceType> found, GenderPolicy inferred) {
        SpaceType primary = selectPrimary(found);
        if (distinctCount(found) < 2) {
            boolean normalized = !BulkImportLabels.spaceType(primary).equalsIgnoreCase(value.trim());
            return new Result(value, primary, inferred, normalized, null, null, List.of(primary));
        }
        String warning = "Multiple space types detected. "
                + BulkImportLabels.spaceType(primary)
                + " selected as the primary type by the import priority rule. Also detected: "
                + labels(others(found, primary))
                + ".";
        return new Result(value, primary, inferred, true, warning, null, found);
    }

    private static SpaceType selectPrimary(List<SpaceType> found) {
        for (SpaceType type : PRIORITY) {
            if (found.contains(type)) {
                return type;
            }
        }
        return found.get(0);
    }

    private static int distinctCount(List<SpaceType> found) {
        return new LinkedHashSet<>(found).size();
    }

    private static List<SpaceType> others(List<SpaceType> found, SpaceType primary) {
        List<SpaceType> others = new ArrayList<>();
        for (SpaceType type : PRIORITY) {
            if (type != primary && found.contains(type)) {
                others.add(type);
            }
        }
        return others;
    }

    private static String typeKey(String value) {
        return PropertyBulkImportValueParser.classificationKey(
                value.replace('–', '-').replace('—', '-'));
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

    private static List<SpaceType> typesIn(String key) {
        Set<SpaceType> found = new LinkedHashSet<>();
        if (isExactCorporateStay(key)) {
            found.add(SpaceType.CORPORATE_STAY);
            return List.copyOf(found);
        }
        if (isAmbiguousCorporate(key)) {
            found.add(SpaceType.CORPORATE_STAY);
            found.add(SpaceType.RENTAL);
        }
        if (PropertyBulkImportValueParser.hasPhrase(key, "co living", "coliving", "co live", "colive")) {
            found.add(SpaceType.CO_LIVING);
        }
        if (isMessSegment(key)) {
            found.add(SpaceType.MESS);
        }
        if (isServicedCorporate(key)) {
            found.add(SpaceType.CORPORATE_STAY);
        } else if (PropertyBulkImportValueParser.hasPhrase(
                        key, "serviced", "rental", "rentals", "apartment", "apartments", "flat", "flats")
                || PropertyBulkImportValueParser.hasWord(key, "rent")) {
            found.add(SpaceType.RENTAL);
        }
        if (PropertyBulkImportValueParser.hasWord(key, "pg") || key.contains("paying guest")) {
            found.add(SpaceType.PG);
        }
        if (PropertyBulkImportValueParser.hasWord(key, "hostel")
                || PropertyBulkImportValueParser.hasWord(key, "hostels")) {
            found.add(SpaceType.HOSTEL);
        }
        if (found.isEmpty()
                && PropertyBulkImportValueParser.isGuestHouseFallback(key)
                && !PropertyBulkImportValueParser.hasWord(key, "corporate")
                && !PropertyBulkImportValueParser.hasWord(key, "company")) {
            found.add(SpaceType.RENTAL);
        }
        return List.copyOf(found);
    }

    /** Food, tiffin, catering, and the meal labels used in the lead workbooks. */
    private static boolean isMessSegment(String key) {
        if (PropertyBulkImportValueParser.hasCoreMessPhrase(key)) {
            return true;
        }
        if (PropertyBulkImportValueParser.hasExplicitLodging(key) || isFoodStallDescriptor(key)) {
            return false;
        }
        return PropertyBulkImportValueParser.hasCateringFoodServicePhrase(key)
                || PropertyBulkImportValueParser.hasWord(key, "food")
                || PropertyBulkImportValueParser.hasWord(key, "fond")
                || PropertyBulkImportValueParser.hasWord(key, "meal")
                || PropertyBulkImportValueParser.hasWord(key, "meals")
                || PropertyBulkImportValueParser.hasPhrase(
                        key, "box lunch", "box lunches", "home food", "home foods", "prepared food", "prepared foods");
    }

    /** Serviced apartment and serviced stay are Corporate Stay, not a rental flat. */
    private static boolean isServicedCorporate(String key) {
        return PropertyBulkImportValueParser.hasPhrase(
                key, "serviced apartment", "serviced apartments", "serviced stay", "serviced stays");
    }

    /** Hotel next to a serviced apartment or serviced stay does not invalidate that corporate type. */
    private static boolean isHotelBesideServicedCorporate(String key, String wholeKey) {
        return PropertyBulkImportValueParser.hasWord(key, "hotel")
                && !PropertyBulkImportValueParser.hasWord(key, "hostel")
                && !PropertyBulkImportValueParser.hasWord(key, "pg")
                && isServicedCorporate(wholeKey);
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

    private static boolean isGenderOnly(String key) {
        if (key.isBlank()) {
            return false;
        }
        for (String word : key.split(" ")) {
            if (!isGenderWord(word)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isGenderWord(String word) {
        return switch (word) {
            case "boy", "boys", "girl", "girls", "gent", "gents", "man", "men", "mens",
                    "male", "female", "lady", "ladies", "woman", "women", "womens" -> true;
            default -> false;
        };
    }

    private static boolean isIgnorableDescriptor(String key, String wholeKey) {
        if (!PropertyBulkImportValueParser.hasCoreMessPhrase(wholeKey)) {
            return false;
        }
        return isFoodStallDescriptor(key) || PropertyBulkImportValueParser.hasPhrase(key, "home food", "home foods");
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
                && !PropertyBulkImportValueParser.hasWord(key, "pg")
                && !isServicedCorporate(key)) {
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
