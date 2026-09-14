package com.acomi.acomi_backend.admin.bulkimport.property;

import com.acomi.acomi_backend.space.api.dto.AmenityAssignmentDto;
import com.acomi.acomi_backend.space.domain.model.AmenityCode;
import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Stateless parsers for Excel cell values used by property bulk import. */
public final class PropertyBulkImportValueParser {

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
     */
    public static PropertyTypeParseResult parsePropertyType(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return PropertyTypeParseResult.blank();
        }
        String key = value.trim().toLowerCase(Locale.ROOT).replace('-', ' ').replace('_', ' ');
        key = key.replaceAll("\\s+", " ");

        return switch (key) {
            case "pg", "paying guest", "paying guest accommodation" -> PropertyTypeParseResult.ok(SpaceType.PG);
            case "hostel" -> PropertyTypeParseResult.ok(SpaceType.HOSTEL);
            case "co living", "coliving", "co living space", "co living accommodation" ->
                    PropertyTypeParseResult.ok(SpaceType.CO_LIVING);
            case "rental", "rent", "apartment", "flat" -> PropertyTypeParseResult.ok(SpaceType.RENTAL);
            case "mess", "canteen", "food mess" -> PropertyTypeParseResult.ok(SpaceType.MESS);
            default -> PropertyTypeParseResult.invalid("Unknown property type: " + value);
        };
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
        String value = blankToNull(raw);
        if (value == null) {
            return BooleanParseResult.blank();
        }
        String key = value.trim().toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        key = key.replaceAll("\\s+", " ");
        return switch (key) {
            case "true", "yes", "y", "1", "included", "food included", "with food" ->
                    BooleanParseResult.ok(Boolean.TRUE);
            case "false", "no", "n", "0", "not included", "without food", "excluded" ->
                    BooleanParseResult.ok(Boolean.FALSE);
            default -> BooleanParseResult.invalid(
                    "Food included must be yes/no or true/false (got: " + value + ")");
        };
    }

    public static GenderParseResult parseGender(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return GenderParseResult.blank();
        }
        String key = value.trim().toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        key = key.replaceAll("\\s+", " ");
        return switch (key) {
            case "male", "m", "gents", "boys", "men", "boy" -> GenderParseResult.ok(GenderPolicy.MALE);
            case "female", "f", "ladies", "girls", "women", "girl" -> GenderParseResult.ok(GenderPolicy.FEMALE);
            case "mixed", "both", "unisex", "any", "all" -> GenderParseResult.ok(GenderPolicy.MIXED);
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
            return BigDecimalParseResult.ok(new BigDecimal(normalized));
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

    public static BigDecimalParseResult parseStartingPrice(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return BigDecimalParseResult.blank();
        }
        try {
            String normalized = value.replace(",", "").trim();
            return BigDecimalParseResult.ok(new BigDecimal(normalized));
        } catch (NumberFormatException ex) {
            return BigDecimalParseResult.invalid("Starting price must be a number (got: " + value + ")");
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
