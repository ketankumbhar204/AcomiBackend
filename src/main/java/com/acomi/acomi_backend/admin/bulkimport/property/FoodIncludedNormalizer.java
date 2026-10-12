package com.acomi.acomi_backend.admin.bulkimport.property;

import java.util.Map;
import java.util.Set;

/**
 * Maps food-included text onto the existing boolean.
 * Included phrases become Yes. Optional, extra-cost, and availability phrases become No with a warning.
 */
public final class FoodIncludedNormalizer {

    private static final Set<String> YES = Set.of(
            "true",
            "yes",
            "y",
            "1",
            "included",
            "food included",
            "food is included",
            "with food",
            "food provided",
            "meals included",
            "all meals",
            "meal included",
            "meals included in rent");

    private static final Set<String> NO = Set.of(
            "false",
            "no",
            "n",
            "0",
            "not included",
            "food not included",
            "no food",
            "without food",
            "food unavailable",
            "no meals",
            "not available",
            "excluded",
            "not included in rent");

    private static final Map<String, String> NO_WITH_WARNING = Map.ofEntries(
            Map.entry("food on demand", "Food is available on demand and is not confirmed as included."),
            Map.entry("on demand", "Food is available on demand and is not confirmed as included."),
            Map.entry("optional", "Food status is optional and was not treated as included."),
            Map.entry("optional food", "Food status is optional and was not treated as included."),
            Map.entry("food optional", "Food status is optional and was not treated as included."),
            Map.entry("optional separate plan", "Food status is optional and was not treated as included."),
            Map.entry("separate plan", "Food status is optional and was not treated as included."),
            Map.entry("no forced mess fee", "Food status is optional and was not treated as included."),
            Map.entry(
                    "food available at extra cost",
                    "Food is available separately/at extra cost; it was not treated as included."),
            Map.entry(
                    "available at extra cost",
                    "Food is available separately/at extra cost; it was not treated as included."),
            Map.entry("extra charge", "Food is available separately/at extra cost; it was not treated as included."),
            Map.entry(
                    "food charge extra",
                    "Food is available separately/at extra cost; it was not treated as included."),
            Map.entry("charged extra", "Food is available separately/at extra cost; it was not treated as included."),
            Map.entry("food extra", "Food is available separately/at extra cost; it was not treated as included."),
            Map.entry("pay extra", "Food is available separately/at extra cost; it was not treated as included."),
            Map.entry("paid extra", "Food is available separately/at extra cost; it was not treated as included."),
            Map.entry(
                    "paid separately",
                    "Food is available separately/at extra cost; it was not treated as included."),
            Map.entry("mess fee extra", "Food is available separately/at extra cost; it was not treated as included."),
            Map.entry(
                    "food available separately",
                    "Food is available separately/at extra cost; it was not treated as included."),
            Map.entry(
                    "meals available separately",
                    "Food is available separately/at extra cost; it was not treated as included."),
            Map.entry("yes add on", "Food is an add-on and was not treated as included."),
            Map.entry("yes addon", "Food is an add-on and was not treated as included."),
            Map.entry("add on", "Food is an add-on and was not treated as included."),
            Map.entry("addon", "Food is an add-on and was not treated as included."),
            Map.entry("tiffin available", "\"Tiffin available\" does not confirm that food is included. Please verify."),
            Map.entry("meals available", "\"Meals available\" does not confirm that meals are included. Please verify."),
            Map.entry("food available", "\"Food available\" does not confirm that food is included. Please verify."),
            Map.entry("home food available", "\"Home food available\" does not confirm that food is included. Please verify."),
            Map.entry(
                    "available",
                    "\"Available\" indicates that food is offered, but does not confirm that food is included. It was not treated as included."),
            Map.entry("breakfast", "\"Breakfast\" indicates a meal type but does not confirm that food is included."));

    public record Result(String original, Boolean value, boolean normalized, String warning, String error) {}

    private FoodIncludedNormalizer() {}

    public static Result normalize(String raw) {
        String value = PropertyBulkImportValueParser.blankToNull(raw);
        if (value == null) {
            return new Result(raw, null, false, null, null);
        }
        String key = PropertyBulkImportValueParser.classificationKey(value);
        if (YES.contains(key)) {
            return decided(value, Boolean.TRUE, null);
        }
        if (NO.contains(key)) {
            return decided(value, Boolean.FALSE, null);
        }
        String warning = NO_WITH_WARNING.get(key);
        if (warning != null) {
            return decided(value, Boolean.FALSE, warning);
        }
        return new Result(
                value,
                null,
                false,
                null,
                "Food included must be yes/no or true/false (got: " + value + ")");
    }

    private static Result decided(String original, Boolean value, String warning) {
        String canonical = BulkImportLabels.food(value);
        boolean normalized = !canonical.equalsIgnoreCase(original.trim());
        return new Result(original, value, normalized, warning, null);
    }
}
