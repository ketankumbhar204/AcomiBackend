package com.acomi.acomi_backend.admin.bulkimport.property;

import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import java.util.Set;

/**
 * Maps Excel gender text onto {@link GenderPolicy}: Gents, Ladies, or Both.
 * Explicit values win. Extra wording becomes a warning instead of a silent guess.
 */
public final class GenderNormalizer {

    private static final Set<String> MALE = Set.of(
            "m",
            "male",
            "male only",
            "gents",
            "gent",
            "men",
            "mens",
            "man",
            "boys",
            "boy",
            "men only",
            "boys only");

    private static final Set<String> FEMALE = Set.of(
            "f",
            "female",
            "female only",
            "ladies",
            "lady",
            "women",
            "womens",
            "woman",
            "girls",
            "girl",
            "women only",
            "girls only",
            "ladies only");

    private static final Set<String> BOTH = Set.of(
            "mixed", "both", "unisex", "any", "all", "mixed gender", "unisex mixed");

    private static final String[] MALE_WORDS = {"male", "gents", "gent", "men", "mens", "man", "boys", "boy"};
    private static final String[] FEMALE_WORDS = {
        "female", "ladies", "lady", "women", "womens", "woman", "girls", "girl"
    };

    public record Result(
            String original, GenderPolicy value, boolean normalized, String warning, String error) {}

    private GenderNormalizer() {}

    public static Result normalize(String raw) {
        String value = PropertyBulkImportValueParser.blankToNull(raw);
        if (value == null) {
            return new Result(raw, null, false, null, null);
        }
        String key = PropertyBulkImportValueParser.classificationKey(value);
        GenderPolicy policy;
        String warning = null;
        if (MALE.contains(key)) {
            policy = GenderPolicy.MALE;
        } else if (FEMALE.contains(key)) {
            policy = GenderPolicy.FEMALE;
        } else if (BOTH.contains(key)) {
            policy = GenderPolicy.MIXED;
        } else {
            boolean male = hasAny(key, MALE_WORDS);
            boolean female = hasAny(key, FEMALE_WORDS);
            if (male && female) {
                policy = GenderPolicy.MIXED;
            } else if (male || female) {
                policy = male ? GenderPolicy.MALE : GenderPolicy.FEMALE;
                if (!leftover(key).isBlank()) {
                    warning = "Gender inferred from \"" + value + "\" as "
                            + BulkImportLabels.gender(policy) + ". Please verify.";
                }
            } else {
                return new Result(value, null, false, null, "Unknown gender policy: " + value);
            }
        }
        boolean normalized = !BulkImportLabels.gender(policy).equalsIgnoreCase(value.trim());
        return new Result(value, policy, normalized, warning, null);
    }

    private static boolean hasAny(String key, String[] words) {
        for (String word : words) {
            if (PropertyBulkImportValueParser.hasWord(key, word)) {
                return true;
            }
        }
        return false;
    }

    private static String leftover(String key) {
        String stripped = key;
        for (String word : MALE_WORDS) {
            stripped = stripWord(stripped, word);
        }
        for (String word : FEMALE_WORDS) {
            stripped = stripWord(stripped, word);
        }
        stripped = stripWord(stripped, "only");
        stripped = stripWord(stripped, "and");
        stripped = stripWord(stripped, "the");
        return stripped.trim();
    }

    private static String stripWord(String key, String word) {
        return key.replaceAll("(?<![a-z0-9])" + word + "(?![a-z0-9])", " ")
                .replaceAll("\\s+", " ");
    }
}
