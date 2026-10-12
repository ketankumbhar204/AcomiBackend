package com.acomi.acomi_backend.admin.bulkimport.property;

import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import com.acomi.acomi_backend.space.domain.model.SpaceType;

/**
 * Maps Excel space-type text onto the existing {@link SpaceType} enum.
 * Explicit aliases only. Combined PG/Hostel keeps the existing PG precedence and warns.
 */
public final class SpaceTypeNormalizer {

    public record Result(
            String original,
            SpaceType type,
            GenderPolicy inferredGender,
            boolean normalized,
            String warning,
            String error) {}

    private SpaceTypeNormalizer() {}

    public static Result normalize(String raw) {
        String value = PropertyBulkImportValueParser.blankToNull(raw);
        if (value == null) {
            return new Result(raw, null, null, false, null, null);
        }
        String key = PropertyBulkImportValueParser.classificationKey(value);
        if (isUnsafe(key)) {
            return invalid(value);
        }
        SpaceType type = resolve(key);
        if (type == null) {
            return invalid(value);
        }
        GenderPolicy inferred = inferGender(key);
        String warning = null;
        boolean pg = PropertyBulkImportValueParser.hasWord(key, "pg") || key.contains("paying guest");
        boolean hostel = PropertyBulkImportValueParser.hasWord(key, "hostel")
                || PropertyBulkImportValueParser.hasWord(key, "hostels");
        if (pg && hostel) {
            warning = "Multiple space types detected: \"" + value
                    + "\". Imported as \"PG\". Please verify.";
        }
        boolean normalized = !BulkImportLabels.spaceType(type).equalsIgnoreCase(value.trim());
        return new Result(value, type, inferred, normalized, warning, null);
    }

    public static String unknownMessage(String value) {
        return "Unknown property type: \"" + value + "\"";
    }

    private static Result invalid(String value) {
        return new Result(value, null, null, false, null, unknownMessage(value));
    }

    private static boolean isUnsafe(String key) {
        boolean namedMessOrLodging = PropertyBulkImportValueParser.hasCoreMessPhrase(key)
                || PropertyBulkImportValueParser.hasExplicitLodging(key);
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
                && !PropertyBulkImportValueParser.hasCoreMessPhrase(key)) {
            return true;
        }
        return key.equals("accommodation")
                || key.equals("property")
                || key.equals("room")
                || key.equals("rooms");
    }

    private static SpaceType resolve(String key) {
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
                        key,
                        "serviced",
                        "corporate apartment",
                        "corporate apartments",
                        "rental",
                        "rentals",
                        "apartment",
                        "apartments",
                        "flat",
                        "flats")
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
        if (PropertyBulkImportValueParser.isGuestHouseFallback(key)) {
            return SpaceType.RENTAL;
        }
        return null;
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
