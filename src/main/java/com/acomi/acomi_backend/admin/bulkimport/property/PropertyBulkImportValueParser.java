package com.acomi.acomi_backend.admin.bulkimport.property;

import com.acomi.acomi_backend.property.domain.model.PriceBasis;
import com.acomi.acomi_backend.space.api.dto.AmenityAssignmentDto;
import com.acomi.acomi_backend.space.domain.model.AmenityCode;
import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stateless parsers for Excel cell values used by property bulk import.
 *
 * <p>Source of truth is the existing domain model, not the import UI labels:
 * space type {@link SpaceType}, gender {@link GenderPolicy}, food included as boolean,
 * sharing and amenities as text (known {@link AmenityCode}s are recognized, unknown text is kept).
 * Safe normalization trims placeholders and formatting. It does not guess meaning
 * (for example food {@code Available} stays invalid).
 */
public final class PropertyBulkImportValueParser {

    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");
    private static final Pattern PRICE_NUMBER = Pattern.compile("\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?");
    private static final Pattern RANGE_SEPARATOR =
            Pattern.compile("\\s*(?:[-–—]|to|/)\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE_SUFFIX = Pattern.compile(
            "(?i)(?:\\s*/\\s*|\\s+per\\s+)(?:person|persons|bed|beds|month|months|night|nights|day|days|room|rooms|order|orders|mo)\\b|/-|\\*");
    private static final Pattern CURRENCY_MARKER = Pattern.compile(
            "(?i)(?:₹|â‚¹|竄ｹ|(?<![a-z])rs\\.?|(?<![a-z])inr(?![a-z]))");

    private PropertyBulkImportValueParser() {}

    /** Treats empty cells and common Excel placeholders (e.g. "-") as absent. */
    public static String blankToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String key = trimmed.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "-",
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
                    "?" -> null;
            default -> trimmed;
        };
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
        SpaceTypeNormalizer.Result normalized = SpaceTypeNormalizer.normalize(raw);
        if (normalized.error() != null) {
            return PropertyTypeParseResult.invalid(normalized.error());
        }
        if (normalized.type() == null) {
            return PropertyTypeParseResult.blank();
        }
        return PropertyTypeParseResult.ok(normalized.type());
    }

    public static BooleanParseResult parseBoolean(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return BooleanParseResult.blank();
        }
        String key = value.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "true", "yes", "y", "1" -> BooleanParseResult.ok(Boolean.TRUE);
            case "false", "no", "n", "0" -> BooleanParseResult.ok(Boolean.FALSE);
            default -> BooleanParseResult.invalid(
                    "Must be true/false, yes/no, y/n, or 1/0 (got: " + value + ")");
        };
    }

    public static BooleanParseResult parseFoodIncluded(String raw) {
        FoodIncludedNormalizer.Result normalized = FoodIncludedNormalizer.normalize(raw);
        if (normalized.error() != null) {
            return BooleanParseResult.invalid(normalized.error());
        }
        return BooleanParseResult.ok(normalized.value());
    }

    public static GenderParseResult parseGender(String raw) {
        GenderNormalizer.Result normalized = GenderNormalizer.normalize(raw);
        if (normalized.error() != null) {
            return GenderParseResult.invalid(normalized.error());
        }
        return GenderParseResult.ok(normalized.value());
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
     * Accepts one starting amount. Currency marks are removed.
     * A single recognized unit ({@code /month}, {@code per person}, {@code /bed}, {@code /room},
     * and the same forms for night, day, and order) does not block the amount.
     * {@code /bed} and {@code /room} still set that billing basis. Other units leave the basis empty
     * so the property type supplies it. Ranges and extra words are not stored.
     */
    public static StartingPriceParseResult parseStartingPrice(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return StartingPriceParseResult.blank();
        }

        String lower = value.toLowerCase(Locale.ROOT);
        boolean perBed = lower.matches(".*(?:/\\s*beds?\\b|\\bper\\s+beds?\\b).*");
        boolean perRoom = lower.matches(".*(?:/\\s*rooms?\\b|\\bper\\s+rooms?\\b).*");

        String working = CURRENCY_MARKER.matcher(value).replaceAll(" ");
        working = PRICE_SUFFIX.matcher(working).replaceAll(" ");
        working = MULTI_SPACE.matcher(working).replaceAll(" ").trim();

        Matcher numbers = PRICE_NUMBER.matcher(working);
        List<String> found = new ArrayList<>();
        while (numbers.find()) {
            found.add(numbers.group());
        }
        if (found.size() >= 2 && RANGE_SEPARATOR.matcher(working).find()) {
            return StartingPriceParseResult.unrepresentable(
                    "Rent range cannot be stored as one starting price (got: " + value + ").");
        }
        if (found.isEmpty()) {
            return StartingPriceParseResult.invalid("Invalid rent value: " + value);
        }
        if (found.size() != 1) {
            return StartingPriceParseResult.unrepresentable(
                    "Rent value has more than one amount and was not stored (got: " + value + ").");
        }

        String leftover = PRICE_NUMBER.matcher(working).replaceAll("");
        leftover = leftover.replaceAll("\\p{Sc}+", "");
        leftover = leftover.replace(",", "").replace(".", "").trim();
        leftover = leftover.replaceAll("[^\\p{L}\\p{N}]+", "").trim();
        if (!leftover.isEmpty() || (perBed && perRoom)) {
            return StartingPriceParseResult.unrepresentable(
                    "Rent unit or qualifier cannot be stored with the current price fields (got: " + value + ").");
        }

        try {
            String numeric = found.get(0).replace(",", "");
            PriceBasis basis = perBed ? PriceBasis.PER_BED : perRoom ? PriceBasis.PER_ROOM : null;
            return StartingPriceParseResult.amount(new BigDecimal(numeric), basis);
        } catch (NumberFormatException ex) {
            return StartingPriceParseResult.invalid("Invalid rent value: " + value);
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
        List<String> unmapped = new ArrayList<>();
        for (String part : parts) {
            String token = blankToNull(part);
            if (token == null) {
                continue;
            }
            Optional<AmenityCode> byEnum = AmenityCode.fromValue(token);
            if (byEnum.isPresent() && byEnum.get() != AmenityCode.CUSTOM) {
                AmenityAssignmentDto dto = new AmenityAssignmentDto();
                dto.setCode(byEnum.get().name());
                dto.setLabel(byEnum.get().getDefaultLabel());
                mapped.add(dto);
                continue;
            }
            Optional<AmenityCode> byLabel = matchAmenityLabel(token);
            if (byLabel.isPresent()) {
                AmenityAssignmentDto dto = new AmenityAssignmentDto();
                dto.setCode(byLabel.get().name());
                dto.setLabel(byLabel.get().getDefaultLabel());
                mapped.add(dto);
            } else {
                unmapped.add(token);
            }
        }
        String unmappedText = unmapped.isEmpty() ? null : String.join(", ", unmapped);
        return AmenitiesParseResult.ok(mapped, unmappedText);
    }

    private static Optional<AmenityCode> matchAmenityLabel(String token) {
        String key = token.trim().toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        key = key.replaceAll("\\s+", " ");
        for (AmenityCode code : AmenityCode.values()) {
            if (code == AmenityCode.CUSTOM || code.getDefaultLabel() == null) {
                continue;
            }
            String label = code.getDefaultLabel().toLowerCase(Locale.ROOT);
            if (label.equals(key) || label.replace(" / ", " ").equals(key)) {
                return Optional.of(code);
            }
        }
        return switch (key) {
            case "wifi", "wi fi", "wi-fi", "internet" -> Optional.of(AmenityCode.WIFI);
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

    static String classificationKey(String value) {
        String key = value.toLowerCase(Locale.ROOT);
        key = key.replace('-', ' ').replace('_', ' ').replace('/', ' ').replace('\\', ' ');
        key = key.replace('.', ' ').replace(',', ' ');
        key = key.replace("'", "").replace("’", "").replace("`", "");
        key = MULTI_SPACE.matcher(key).replaceAll(" ").trim();
        key = key.replaceAll("\\bp\\s+g\\b", "pg");
        return key;
    }

    static boolean hasCoreMessPhrase(String key) {
        return hasPhrase(key, "mess", "tiffin", "meal service", "meal services", "canteen", "food mess");
    }

    static boolean hasCateringFoodServicePhrase(String key) {
        return hasPhrase(key, "catering", "caterer", "food service", "food services");
    }

    static boolean hasExplicitLodging(String key) {
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
    static boolean isGuestHouseFallback(String key) {
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

    static boolean hasPhrase(String key, String... phrases) {
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

    static boolean hasWord(String key, String word) {
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

        /** Reserved for values that parse, but should also be kept as a note. */
        public String note() {
            return null;
        }
    }

    public static String normalizeFreeText(String raw) {
        return blankToNull(raw);
    }

    public static String mergeUnmappedNotes(String existing, String note) {
        String extra = blankToNull(note);
        if (extra == null) {
            return existing;
        }
        String current = blankToNull(existing);
        if (current == null) {
            return extra;
        }
        return current + "\n" + extra;
    }

    public enum PhoneKind {
        MOBILE,
        LANDLINE
    }

    public record PhoneParseResult(String value, String error, PhoneKind kind) {
        public static PhoneParseResult blank() {
            return new PhoneParseResult(null, null, null);
        }

        public static PhoneParseResult invalid(String message) {
            return new PhoneParseResult(null, message, null);
        }

        public boolean isBlank() {
            return value == null && error == null;
        }

        public boolean isInvalid() {
            return error != null;
        }
    }

    /**
     * Indian mobiles become the contact field. Other digit strings are landlines and
     * stay in notes, because registration validation only accepts {@code [6-9][0-9]{9}}.
     */
    public static PhoneParseResult parseContactPhone(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return PhoneParseResult.blank();
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (digits.length() > 10 && digits.startsWith("91")) {
            digits = digits.substring(digits.length() - 10);
        }
        if (digits.matches("[6-9]\\d{9}")) {
            return new PhoneParseResult(digits, null, PhoneKind.MOBILE);
        }
        if (digits.length() >= 6 && digits.length() <= 15) {
            return new PhoneParseResult(digits, null, PhoneKind.LANDLINE);
        }
        return PhoneParseResult.invalid("Enter a valid phone number (got: " + value + ")");
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

    /**
     * A rent cell. {@code warning} means the amount was not stored because the unit or range
     * cannot be represented. {@code basis} is set only for per-bed and per-room amounts.
     */
    public record StartingPriceParseResult(BigDecimal value, PriceBasis basis, String warning, String error) {
        public static StartingPriceParseResult blank() {
            return new StartingPriceParseResult(null, null, null, null);
        }

        public static StartingPriceParseResult amount(BigDecimal value, PriceBasis basis) {
            return new StartingPriceParseResult(value, basis, null, null);
        }

        public static StartingPriceParseResult unrepresentable(String warning) {
            return new StartingPriceParseResult(null, null, warning, null);
        }

        public static StartingPriceParseResult invalid(String message) {
            return new StartingPriceParseResult(null, null, null, message);
        }

        public boolean isInvalid() {
            return error != null;
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
