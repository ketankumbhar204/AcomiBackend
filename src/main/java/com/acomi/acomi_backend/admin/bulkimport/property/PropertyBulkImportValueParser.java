package com.acomi.acomi_backend.admin.bulkimport.property;

import com.acomi.acomi_backend.space.api.dto.AmenityAssignmentDto;
import com.acomi.acomi_backend.space.domain.model.AmenityCode;
import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Stateless parsers for Excel cell values used by property bulk import. */
public final class PropertyBulkImportValueParser {

    private static final Pattern MOBILE_CANONICAL = Pattern.compile("^[6-9]\\d{9}$");
    private static final Pattern PRICE_NUMBER = Pattern.compile("\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?");
    private static final Pattern RANGE_SEPARATOR = Pattern.compile("\\s*(?:[-–—]|to)\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE_SUFFIX = Pattern.compile(
            "(?i)(?:\\s*/\\s*|\\s+per\\s+)(?:bed|month|night|day|room)\\b|/-|\\*");
    private static final Pattern CURRENCY_MARKER = Pattern.compile(
            "(?i)(?:₹|â‚¹|竄ｹ|(?<![a-z])rs\\.?|(?<![a-z])inr(?![a-z]))");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");
    private static final Set<String> BLANK_MARKERS = Set.of(
            "-",
            "--",
            "---",
            "—",
            "–",
            ".",
            "_",
            "n/a",
            "na",
            "n.a.",
            "n.a",
            "none",
            "null",
            "nil",
            "blank",
            "empty",
            "?");

    private PropertyBulkImportValueParser() {}

    /**
     * Safe shared text cleanup used before field-specific parsing.
     * Does not treat arbitrary text as blank.
     */
    public static String normalizeInput(String raw) {
        if (raw == null) {
            return null;
        }
        // NFC only — NFKC would turn superscript ¹ in mojibake "â‚¹" into the digit 1.
        String value = Normalizer.normalize(raw, Normalizer.Form.NFC);
        value = value.replace('\u00a0', ' ').replace('\u202f', ' ').replace('\u2007', ' ');
        value = value.replace('\u2011', '-');
        value = value.replace('\u2018', '\'').replace('\u2019', '\'');
        value = value.replace('\u201c', '"').replace('\u201d', '"');
        value = MULTI_SPACE.matcher(value.trim()).replaceAll(" ");
        return value.isEmpty() ? "" : value;
    }

    /** Treats empty cells and common Excel placeholders (e.g. "-") as absent. */
    public static String blankToNull(String raw) {
        String normalized = normalizeInput(raw);
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        String key = normalized.toLowerCase(Locale.ROOT);
        if (BLANK_MARKERS.contains(key)) {
            return null;
        }
        return normalized;
    }

    /** Collapses whitespace for free-text fields (sharing) without changing meaning. */
    public static String normalizeFreeText(String raw) {
        return blankToNull(raw);
    }

    /**
     * Normalizes property type labels to {@link SpaceType}.
     * Mess is a valid type result for mixed-Excel routing (not an invalid property type).
     *
     * <p>Compound labels use a deterministic priority:
     * co-living → mess/tiffin/meal service → serviced/rental/apartment → pg+hostel → pg → hostel
     * → guest-house fallback (RENTAL).
     *
     * <p>Catering / food-service phrases map to MESS only when no explicit lodging category
     * is present, so {@code PG / Food Service} stays PG.
     */
    public static PropertyTypeParseResult parsePropertyType(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return PropertyTypeParseResult.blank();
        }
        String key = classificationKey(value);

        if (hasPhrase(key, "co living", "coliving")) {
            return PropertyTypeParseResult.ok(SpaceType.CO_LIVING);
        }
        if (hasCoreMessPhrase(key)
                || (hasCateringFoodServicePhrase(key) && !hasExplicitLodging(key))) {
            return PropertyTypeParseResult.ok(SpaceType.MESS);
        }
        if (hasPhrase(
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
                || hasWord(key, "rent")) {
            return PropertyTypeParseResult.ok(SpaceType.RENTAL);
        }
        boolean pg = hasWord(key, "pg") || key.contains("paying guest");
        boolean hostel = hasWord(key, "hostel") || hasWord(key, "hostels");
        if (pg && hostel) {
            return PropertyTypeParseResult.ok(SpaceType.PG);
        }
        if (pg) {
            return PropertyTypeParseResult.ok(SpaceType.PG);
        }
        if (hostel) {
            return PropertyTypeParseResult.ok(SpaceType.HOSTEL);
        }
        if (isGuestHouseFallback(key)) {
            return PropertyTypeParseResult.ok(SpaceType.RENTAL);
        }
        return PropertyTypeParseResult.invalid("Unknown property type: " + value);
    }

    public static BooleanParseResult parseBoolean(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return BooleanParseResult.blank();
        }
        String key = value.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "true", "yes", "y", "1" -> BooleanParseResult.ok(Boolean.TRUE);
            case "false", "no", "n", "0" -> BooleanParseResult.ok(Boolean.FALSE);
            default -> BooleanParseResult.invalid(
                    "Must be true/false, yes/no, y/n, or 1/0 (got: " + value + ")");
        };
    }

    public static FoodParseResult parseFoodIncluded(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return FoodParseResult.blank();
        }
        String key = classificationKey(value);

        if (isFoodNo(key)) {
            String note = isCanonicalFoodAlias(key) ? null : value;
            return FoodParseResult.ok(Boolean.FALSE, note);
        }
        if (isFoodYes(key)) {
            String note = isCanonicalFoodAlias(key) ? null : value;
            return FoodParseResult.ok(Boolean.TRUE, note);
        }
        return FoodParseResult.invalid("Food included must be yes/no or true/false (got: " + value + ")");
    }

    public static GenderParseResult parseGender(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return GenderParseResult.blank();
        }
        String key = classificationKey(value);
        return switch (key) {
            case "male", "m", "gents", "boys", "men", "boy", "man" -> GenderParseResult.ok(GenderPolicy.MALE);
            case "female", "f", "ladies", "girls", "women", "girl", "woman", "lady" ->
                    GenderParseResult.ok(GenderPolicy.FEMALE);
            case "mixed", "both", "unisex", "any", "all", "mixed gender", "unisex mixed" ->
                    GenderParseResult.ok(GenderPolicy.MIXED);
            default -> GenderParseResult.invalid("Unknown gender policy: " + value);
        };
    }

    public static BigDecimalParseResult parseCoordinate(String raw, String fieldLabel) {
        String value = blankToNull(raw);
        if (value == null) {
            return BigDecimalParseResult.blank();
        }
        try {
            String normalized = value.replace(",", "").trim();
            BigDecimal number = new BigDecimal(normalized);
            if ("Latitude".equals(fieldLabel)
                    && (number.compareTo(new BigDecimal("-90")) < 0
                            || number.compareTo(new BigDecimal("90")) > 0)) {
                return BigDecimalParseResult.invalid(fieldLabel + " is out of range (got: " + value + ")");
            }
            if ("Longitude".equals(fieldLabel)
                    && (number.compareTo(new BigDecimal("-180")) < 0
                            || number.compareTo(new BigDecimal("180")) > 0)) {
                return BigDecimalParseResult.invalid(fieldLabel + " is out of range (got: " + value + ")");
            }
            return BigDecimalParseResult.ok(number);
        } catch (NumberFormatException ex) {
            return BigDecimalParseResult.invalid(fieldLabel + " must be a number (got: " + value + ")");
        }
    }

    /**
     * Normalizes phone cells to a 10-digit Indian national number for storage.
     * Accepts bare 10-digit values, {@code +91}/{@code 91} prefixes, and a leading {@code 0}.
     * Country code is not stored — it is implied (+91) and added only when displaying.
     *
     * <p>Landlines are not returned here (they fail the mobile regex). Use
     * {@link #parseContactPhone(String)} so bulk import can accept them without failing the row.
     */
    public static String stripMobileDigits(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (digits.length() > 10 && digits.startsWith("91")) {
            digits = digits.substring(digits.length() - 10);
        }
        return digits;
    }

    /**
     * Parses an Indian mobile or STD landline.
     * Mobiles are canonical 10-digit {@code [6-9]…} values. Landlines keep the leading {@code 0}
     * (e.g. {@code 02065328521}) and must not be written to the mobile DTO field.
     */
    public static PhoneParseResult parseContactPhone(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return PhoneParseResult.blank();
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return PhoneParseResult.invalid("Invalid contact number: " + value);
        }
        if (isTollFreeNumber(digits)) {
            return PhoneParseResult.invalid("Invalid contact number: " + value);
        }

        String mobile = toCanonicalMobile(digits);
        if (mobile != null) {
            return PhoneParseResult.mobile(mobile);
        }
        String landline = toCanonicalLandline(digits);
        if (landline != null) {
            return PhoneParseResult.landline(landline);
        }
        return PhoneParseResult.invalid("Invalid contact number: " + value);
    }

    public static BigDecimalParseResult parseStartingPrice(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return BigDecimalParseResult.blank();
        }

        String working = CURRENCY_MARKER.matcher(value).replaceAll(" ");
        working = PRICE_SUFFIX.matcher(working).replaceAll(" ");
        working = MULTI_SPACE.matcher(working).replaceAll(" ").trim();

        Matcher numbers = PRICE_NUMBER.matcher(working);
        List<String> found = new ArrayList<>();
        while (numbers.find()) {
            found.add(numbers.group());
        }
        if (found.size() >= 2 && RANGE_SEPARATOR.matcher(working).find()) {
            return BigDecimalParseResult.invalid(
                    "Rent range cannot be represented by a single starting price (got: " + value + ")");
        }
        if (found.size() != 1) {
            return BigDecimalParseResult.invalid("Invalid rent value: " + value);
        }

        String leftover = PRICE_NUMBER.matcher(working).replaceAll("");
        leftover = leftover.replace(",", "").replace(".", "").trim();
        if (!leftover.isEmpty()) {
            return BigDecimalParseResult.invalid("Invalid rent value: " + value);
        }

        try {
            String numeric = found.get(0).replace(",", "");
            return BigDecimalParseResult.ok(new BigDecimal(numeric));
        } catch (NumberFormatException ex) {
            return BigDecimalParseResult.invalid("Invalid rent value: " + value);
        }
    }

    /** Splits amenity text into known codes and leftover unmapped labels. */
    public static AmenitiesParseResult parseAmenities(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return AmenitiesParseResult.blank();
        }
        String[] parts = value.split("[,;/|]+");
        List<AmenityAssignmentDto> mapped = new ArrayList<>();
        Set<String> seenCodes = new LinkedHashSet<>();
        Set<String> unmapped = new LinkedHashSet<>();
        for (String part : parts) {
            String token = blankToNull(part);
            if (token == null) {
                continue;
            }
            Optional<AmenityCode> byEnum = AmenityCode.fromValue(token);
            if (byEnum.isPresent() && byEnum.get() != AmenityCode.CUSTOM) {
                addAmenity(mapped, seenCodes, byEnum.get());
                continue;
            }
            Optional<AmenityCode> byLabel = matchAmenityLabel(token);
            if (byLabel.isPresent()) {
                addAmenity(mapped, seenCodes, byLabel.get());
            } else {
                unmapped.add(token);
            }
        }
        String unmappedText = unmapped.isEmpty() ? null : String.join(", ", unmapped);
        return AmenitiesParseResult.ok(mapped, unmappedText);
    }

    public static String mergeUnmappedNotes(String existing, String addition) {
        String left = blankToNull(existing);
        String right = blankToNull(addition);
        if (left == null) {
            return right;
        }
        if (right == null || left.contains(right)) {
            return left;
        }
        return left + "; " + right;
    }

    private static void addAmenity(
            List<AmenityAssignmentDto> mapped, Set<String> seenCodes, AmenityCode code) {
        if (!seenCodes.add(code.name())) {
            return;
        }
        AmenityAssignmentDto dto = new AmenityAssignmentDto();
        dto.setCode(code.name());
        dto.setLabel(code.getDefaultLabel());
        mapped.add(dto);
    }

    private static Optional<AmenityCode> matchAmenityLabel(String token) {
        String key = classificationKey(token);
        for (AmenityCode code : AmenityCode.values()) {
            if (code == AmenityCode.CUSTOM || code.getDefaultLabel() == null) {
                continue;
            }
            String label = classificationKey(code.getDefaultLabel());
            if (label.equals(key)) {
                return Optional.of(code);
            }
        }
        return switch (key) {
            case "wifi", "wi fi", "internet" -> Optional.of(AmenityCode.WIFI);
            case "food", "food included", "meals" -> Optional.of(AmenityCode.FOOD_INCLUDED);
            case "washing machine", "laundry", "washer" -> Optional.of(AmenityCode.WASHING_MACHINE);
            case "hot water", "geyser" -> Optional.of(AmenityCode.HOT_WATER);
            case "parking", "car parking", "bike parking" -> Optional.of(AmenityCode.PARKING);
            case "fridge", "refrigerator" -> Optional.of(AmenityCode.REFRIGERATOR);
            case "housekeeping", "cleaning" -> Optional.of(AmenityCode.HOUSEKEEPING);
            case "cctv", "camera", "security camera" -> Optional.of(AmenityCode.CCTV);
            case "power backup", "generator", "inverter" -> Optional.of(AmenityCode.POWER_BACKUP);
            case "ro", "ro water", "drinking water" -> Optional.of(AmenityCode.RO_WATER);
            case "bed", "beds" -> Optional.of(AmenityCode.BEDS);
            case "wardrobe", "cupboard", "almirah" -> Optional.of(AmenityCode.WARDROBE);
            default -> Optional.empty();
        };
    }

    private static String classificationKey(String value) {
        String key = value.toLowerCase(Locale.ROOT);
        key = key.replace('-', ' ').replace('_', ' ').replace('/', ' ').replace('\\', ' ');
        key = key.replace('.', ' ').replace(',', ' ');
        key = MULTI_SPACE.matcher(key).replaceAll(" ").trim();
        key = key.replaceAll("\\bp\\s+g\\b", "pg");
        return key;
    }

    private static boolean hasCoreMessPhrase(String key) {
        return hasPhrase(key, "mess", "tiffin", "meal service", "meal services", "canteen", "food mess");
    }

    private static boolean hasCateringFoodServicePhrase(String key) {
        return hasPhrase(key, "catering", "caterer", "food service", "food services");
    }

    private static boolean hasExplicitLodging(String key) {
        return hasPhrase(key, "co living", "coliving")
                || hasWord(key, "pg")
                || key.contains("paying guest")
                || hasWord(key, "hostel")
                || hasWord(key, "hostels")
                || hasPhrase(
                        key,
                        "serviced",
                        "rental",
                        "rentals",
                        "apartment",
                        "apartments",
                        "flat",
                        "flats")
                || hasWord(key, "rent");
    }

    /**
     * Guest house is not a first-class type. Map to RENTAL only when no stronger
     * lodging/food category is present. Hotel is intentionally not included.
     */
    private static boolean isGuestHouseFallback(String key) {
        if (!hasPhrase(key, "guest house", "guesthouse")) {
            return false;
        }
        return !hasWord(key, "pg")
                && !key.contains("paying guest")
                && !hasWord(key, "hostel")
                && !hasWord(key, "hostels")
                && !hasPhrase(key, "co living", "coliving")
                && !hasPhrase(key, "mess", "tiffin");
    }

    private static boolean hasPhrase(String key, String... phrases) {
        for (String phrase : phrases) {
            if (phrase.indexOf(' ') >= 0) {
                if (key.contains(phrase)) {
                    return true;
                }
            } else if (hasWord(key, phrase)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasWord(String key, String word) {
        int from = 0;
        while (from <= key.length() - word.length()) {
            int at = key.indexOf(word, from);
            if (at < 0) {
                return false;
            }
            boolean startOk = at == 0 || !Character.isLetterOrDigit(key.charAt(at - 1));
            int end = at + word.length();
            boolean endOk = end == key.length() || !Character.isLetterOrDigit(key.charAt(end));
            if (startOk && endOk) {
                return true;
            }
            from = at + 1;
        }
        return false;
    }

    private static boolean isFoodYes(String key) {
        return switch (key) {
            case "true",
                    "yes",
                    "y",
                    "1",
                    "included",
                    "food included",
                    "with food",
                    "included in rent",
                    "breakfast included",
                    "meals included",
                    "meal included",
                    "tiffin included",
                    "lunch included",
                    "dinner included" -> true;
            default -> false;
        };
    }

    private static boolean isFoodNo(String key) {
        return switch (key) {
            case "false",
                    "no",
                    "n",
                    "0",
                    "not included",
                    "without food",
                    "excluded",
                    "food not included",
                    "extra charge",
                    "paid separately",
                    "not included in rent" -> true;
            default -> false;
        };
    }

    private static boolean isCanonicalFoodAlias(String key) {
        return switch (key) {
            case "true",
                    "false",
                    "yes",
                    "no",
                    "y",
                    "n",
                    "1",
                    "0",
                    "included",
                    "food included",
                    "with food",
                    "not included",
                    "without food",
                    "excluded" -> true;
            default -> false;
        };
    }

    private static String toCanonicalMobile(String digits) {
        String candidate = digits;
        if (candidate.length() == 11 && candidate.startsWith("0")) {
            candidate = candidate.substring(1);
        }
        if (candidate.length() > 10 && candidate.startsWith("91")) {
            candidate = candidate.substring(candidate.length() - 10);
        }
        return MOBILE_CANONICAL.matcher(candidate).matches() ? candidate : null;
    }

    /**
     * Indian STD landline. Strips a leading {@code 91} country code once (never “last 10”).
     * Toll-free {@code 1800}/{@code 1860} numbers are rejected by {@link #isTollFreeNumber}.
     */
    private static String toCanonicalLandline(String digits) {
        if (looksLikeNationalLandline(digits)) {
            return digits;
        }
        String national = stripCountryCodeOnce(digits);
        if (national.equals(digits)) {
            return landlineByPrependingZero(national);
        }
        if (looksLikeNationalLandline(national)) {
            return national;
        }
        return landlineByPrependingZero(national);
    }

    private static boolean looksLikeNationalLandline(String digits) {
        return digits.length() >= 10
                && digits.length() <= 12
                && digits.startsWith("0")
                && digits.charAt(1) >= '1'
                && digits.charAt(1) <= '5';
    }

    private static String landlineByPrependingZero(String national) {
        if (national.length() == 10
                && national.charAt(0) >= '1'
                && national.charAt(0) <= '5'
                && !national.startsWith("1800")
                && !national.startsWith("1860")) {
            return "0" + national;
        }
        return null;
    }

    /** Strips a single leading {@code 91} country code. Does not take the last 10 digits. */
    private static String stripCountryCodeOnce(String digits) {
        if (digits.startsWith("91") && digits.length() > 10) {
            return digits.substring(2);
        }
        return digits;
    }

    private static boolean isTollFreeNumber(String digits) {
        String national = stripCountryCodeOnce(digits);
        return national.startsWith("1800") || national.startsWith("1860");
    }

    public record PropertyTypeParseResult(SpaceType value, String error) {
        public static PropertyTypeParseResult blank() {
            return new PropertyTypeParseResult(null, null);
        }

        public static PropertyTypeParseResult ok(SpaceType type) {
            return new PropertyTypeParseResult(type, null);
        }

        public static PropertyTypeParseResult invalid(String message) {
            return new PropertyTypeParseResult(null, message);
        }

        public boolean isInvalid() {
            return error != null;
        }
    }

    public record BooleanParseResult(Boolean value, String error) {
        public static BooleanParseResult blank() {
            return new BooleanParseResult(null, null);
        }

        public static BooleanParseResult ok(Boolean value) {
            return new BooleanParseResult(value, null);
        }

        public static BooleanParseResult invalid(String message) {
            return new BooleanParseResult(null, message);
        }

        public boolean isInvalid() {
            return error != null;
        }
    }

    public record FoodParseResult(Boolean value, String note, String error) {
        public static FoodParseResult blank() {
            return new FoodParseResult(null, null, null);
        }

        public static FoodParseResult ok(Boolean value, String note) {
            return new FoodParseResult(value, note, null);
        }

        public static FoodParseResult invalid(String message) {
            return new FoodParseResult(null, null, message);
        }

        public boolean isInvalid() {
            return error != null;
        }
    }

    public enum PhoneKind {
        MOBILE,
        LANDLINE
    }

    public record PhoneParseResult(PhoneKind kind, String value, String error) {
        public static PhoneParseResult blank() {
            return new PhoneParseResult(null, null, null);
        }

        public static PhoneParseResult mobile(String value) {
            return new PhoneParseResult(PhoneKind.MOBILE, value, null);
        }

        public static PhoneParseResult landline(String value) {
            return new PhoneParseResult(PhoneKind.LANDLINE, value, null);
        }

        public static PhoneParseResult invalid(String message) {
            return new PhoneParseResult(null, null, message);
        }

        public boolean isInvalid() {
            return error != null;
        }

        public boolean isBlank() {
            return error == null && value == null;
        }
    }

    public record GenderParseResult(GenderPolicy value, String error) {
        public static GenderParseResult blank() {
            return new GenderParseResult(null, null);
        }

        public static GenderParseResult ok(GenderPolicy value) {
            return new GenderParseResult(value, null);
        }

        public static GenderParseResult invalid(String message) {
            return new GenderParseResult(null, message);
        }

        public boolean isInvalid() {
            return error != null;
        }
    }

    public record BigDecimalParseResult(BigDecimal value, String error) {
        public static BigDecimalParseResult blank() {
            return new BigDecimalParseResult(null, null);
        }

        public static BigDecimalParseResult ok(BigDecimal value) {
            return new BigDecimalParseResult(value, null);
        }

        public static BigDecimalParseResult invalid(String message) {
            return new BigDecimalParseResult(null, message);
        }

        public boolean isInvalid() {
            return error != null;
        }

        public Optional<BigDecimal> optional() {
            return Optional.ofNullable(value);
        }
    }

    public record AmenitiesParseResult(List<AmenityAssignmentDto> amenities, String unmappedAmenities, String error) {
        public static AmenitiesParseResult blank() {
            return new AmenitiesParseResult(List.of(), null, null);
        }

        public static AmenitiesParseResult ok(List<AmenityAssignmentDto> amenities, String unmapped) {
            return new AmenitiesParseResult(List.copyOf(amenities), unmapped, null);
        }

        public boolean isInvalid() {
            return error != null;
        }
    }
}
