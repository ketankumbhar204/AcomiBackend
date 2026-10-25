package com.acomi.acomi_backend.admin.bulkimport.property;

import com.acomi.acomi_backend.admin.bulkimport.property.dto.PropertyBulkImportFieldError;
import com.acomi.acomi_backend.admin.bulkimport.property.dto.PropertyBulkImportFieldNotice;
import com.acomi.acomi_backend.mess.api.dto.request.AdminCreateMessRegistrationRequest;
import com.acomi.acomi_backend.property.api.dto.request.AdminCreatePropertyRegistrationRequest;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps a raw Excel data row through a field→header mapping into a validated property or mess
 * request (or BLANK / INVALID outcome).
 */
public final class PropertyBulkImportRowProcessor {

    public enum RowStatus {
        VALID,
        WARNING,
        INVALID,
        BLANK
    }

    public enum TargetKind {
        PROPERTY,
        MESS
    }

    public record ProcessedRow(
            RowStatus status,
            TargetKind targetKind,
            Map<String, String> valuesSnapshot,
            AdminCreatePropertyRegistrationRequest propertyRequest,
            AdminCreateMessRegistrationRequest messRequest,
            List<PropertyBulkImportFieldError> errors,
            List<PropertyBulkImportFieldNotice> notices,
            Map<String, String> normalizedValues) {

        /** Convenience for property-only callers/tests. */
        public AdminCreatePropertyRegistrationRequest request() {
            return propertyRequest;
        }
    }

    private PropertyBulkImportRowProcessor() {}

    public static ProcessedRow process(
            Map<String, String> excelRow,
            Map<String, String> mapping,
            Validator validator) {
        return process(excelRow, mapping, validator, false);
    }

    public static ProcessedRow process(
            Map<String, String> excelRow,
            Map<String, String> mapping,
            Validator validator,
            boolean markAsTestLead) {
        return process(excelRow, mapping, validator, markAsTestLead, Map.of());
    }

    public static ProcessedRow process(
            Map<String, String> excelRow,
            Map<String, String> mapping,
            Validator validator,
            boolean markAsTestLead,
            Map<String, String> fieldOverrides) {
        Map<PropertyBulkImportField, String> rawByField = extractMappedValues(excelRow, mapping);
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (PropertyBulkImportField field : PropertyBulkImportField.values()) {
            String raw = rawByField.get(field);
            snapshot.put(field.name(), raw == null ? "" : raw);
        }

        if (isCompletelyBlank(rawByField)) {
            return new ProcessedRow(RowStatus.BLANK, null, snapshot, null, null, List.of(), List.of(), Map.of());
        }
        applyFieldOverrides(rawByField, fieldOverrides);

        List<PropertyBulkImportFieldError> errors = new ArrayList<>();
        List<PropertyBulkImportFieldNotice> notices = new ArrayList<>();
        Map<String, String> normalizedValues = new LinkedHashMap<>();

        SpaceTypeNormalizer.Result typeResult = SpaceTypeNormalizer.normalize(
                rawByField.get(PropertyBulkImportField.PROPERTY_TYPE));
        if (typeResult.error() != null) {
            errors.add(new PropertyBulkImportFieldError(
                    PropertyBulkImportField.PROPERTY_TYPE.name(), typeResult.error()));
        } else if (typeResult.type() != null) {
            remember(normalizedValues, PropertyBulkImportField.PROPERTY_TYPE, typeResult.normalized(),
                    BulkImportLabels.spaceType(typeResult.type()));
            if (typeResult.warning() != null) {
                notices.add(notice(
                        PropertyBulkImportField.PROPERTY_TYPE,
                        "WARNING",
                        typeResult.warning(),
                        typeResult.original(),
                        BulkImportLabels.spaceType(typeResult.type())));
            }
        }

        GenderNormalizer.Result genderResult = GenderNormalizer.normalize(
                rawByField.get(PropertyBulkImportField.GENDER));
        com.acomi.acomi_backend.space.domain.model.GenderPolicy gender = genderResult.value();
        if (genderResult.error() != null) {
            errors.add(new PropertyBulkImportFieldError(
                    PropertyBulkImportField.GENDER.name(), genderResult.error()));
        } else if (genderResult.warning() != null && gender == null) {
            notices.add(notice(
                    PropertyBulkImportField.GENDER,
                    "WARNING",
                    genderResult.warning(),
                    genderResult.original(),
                    null));
        } else if (gender == null && typeResult.inferredGender() != null && typeResult.error() == null) {
            gender = typeResult.inferredGender();
            String label = BulkImportLabels.gender(gender);
            normalizedValues.put(PropertyBulkImportField.GENDER.name(), label);
            notices.add(notice(
                    PropertyBulkImportField.GENDER,
                    "NORMALIZED",
                    "Gender '" + label + "' was inferred from the property type '"
                            + typeResult.original() + "'.",
                    typeResult.original(),
                    label));
        } else if (gender != null && typeResult.inferredGender() != null && gender != typeResult.inferredGender()) {
            notices.add(notice(
                    PropertyBulkImportField.GENDER,
                    "WARNING",
                    "Space Type '" + typeResult.original() + "' suggests "
                            + BulkImportLabels.gender(typeResult.inferredGender())
                            + ", but Gender is explicitly '" + genderResult.original()
                            + "'. Please verify.",
                    genderResult.original(),
                    BulkImportLabels.gender(gender)));
        } else if (genderResult.warning() != null) {
            notices.add(notice(
                    PropertyBulkImportField.GENDER,
                    "WARNING",
                    genderResult.warning(),
                    genderResult.original(),
                    BulkImportLabels.gender(gender)));
            remember(normalizedValues, PropertyBulkImportField.GENDER, true, BulkImportLabels.gender(gender));
        } else if (gender != null) {
            remember(normalizedValues, PropertyBulkImportField.GENDER, genderResult.normalized(),
                    BulkImportLabels.gender(gender));
        }

        FoodIncludedNormalizer.Result foodResult = FoodIncludedNormalizer.normalize(
                rawByField.get(PropertyBulkImportField.FOOD_INCLUDED));
        if (foodResult.error() != null) {
            errors.add(new PropertyBulkImportFieldError(
                    PropertyBulkImportField.FOOD_INCLUDED.name(), foodResult.error()));
        } else if (foodResult.warning() != null && foodResult.value() == null) {
            notices.add(notice(
                    PropertyBulkImportField.FOOD_INCLUDED,
                    "WARNING",
                    foodResult.warning(),
                    foodResult.original(),
                    null));
        } else if (foodResult.value() != null) {
            String foodLabel = BulkImportLabels.food(foodResult.value());
            remember(normalizedValues, PropertyBulkImportField.FOOD_INCLUDED, foodResult.normalized(), foodLabel);
            if (foodResult.warning() != null) {
                notices.add(notice(
                        PropertyBulkImportField.FOOD_INCLUDED,
                        "WARNING",
                        foodResult.warning(),
                        foodResult.original(),
                        foodLabel));
            }
        }

        boolean mess = typeResult.type() == SpaceType.MESS;
        SharedParsedFields shared = parseSharedFields(rawByField, errors, notices, gender, foodResult.value());
        if (shared.startingPrice() != null) {
            String shown = shared.startingPrice().stripTrailingZeros().toPlainString();
            String original = PropertyBulkImportValueParser.blankToNull(
                    rawByField.get(PropertyBulkImportField.STARTING_PRICE));
            if (original != null && !shown.equals(original)) {
                normalizedValues.put(PropertyBulkImportField.STARTING_PRICE.name(), shown);
            }
        }
        addBlankWarnings(mapping, rawByField, typeResult, gender, foodResult, notices);
        addFoodAmenityWarnings(rawByField, foodResult, notices);

        if (mess) {
            AdminCreateMessRegistrationRequest request = new AdminCreateMessRegistrationRequest();
            applySharedToMess(request, shared);
            request.setMessName(PropertyBulkImportValueParser.blankToNull(
                    rawByField.get(PropertyBulkImportField.PROPERTY_NAME)));
            if (shared.startingPrice() != null) {
                request.setMonthlyPrice(shared.startingPrice());
                request.setMonthlyPriceBasis(shared.priceBasis());
                request.setSuppressPriceBasisDefault(
                        shared.priceBasis() == null && !shared.useTypeDefault());
            }
            if (markAsTestLead) {
                request.setTestLead(true);
                snapshot.put(PropertyBulkImportField.TEST_LEAD.name(), "true");
            }

            if (validator != null) {
                addViolations(validator.validate(request), errors);
            }
            return finish(TargetKind.MESS, snapshot, null, request, errors, notices, normalizedValues);
        }

        AdminCreatePropertyRegistrationRequest request = new AdminCreatePropertyRegistrationRequest();
        if (typeResult.type() != null) {
            request.setPropertyType(typeResult.type());
        }
        applySharedToProperty(request, shared);
        request.setPropertyName(PropertyBulkImportValueParser.blankToNull(
                rawByField.get(PropertyBulkImportField.PROPERTY_NAME)));
        request.setStartingPrice(shared.startingPrice());
        request.setPriceBasis(shared.priceBasis());
        request.setSuppressPriceBasisDefault(
                shared.startingPrice() != null && shared.priceBasis() == null && !shared.useTypeDefault());
        request.setAmenities(shared.amenities());
        if (markAsTestLead) {
            request.setTestLead(true);
            snapshot.put(PropertyBulkImportField.TEST_LEAD.name(), "true");
        }
        if (validator != null) {
            addViolations(validator.validate(request), errors);
        }
        return finish(TargetKind.PROPERTY, snapshot, request, null, errors, notices, normalizedValues);
    }

    private static ProcessedRow finish(
            TargetKind kind,
            Map<String, String> snapshot,
            AdminCreatePropertyRegistrationRequest propertyRequest,
            AdminCreateMessRegistrationRequest messRequest,
            List<PropertyBulkImportFieldError> errors,
            List<PropertyBulkImportFieldNotice> notices,
            Map<String, String> normalizedValues) {
        RowStatus status;
        if (!errors.isEmpty()) {
            status = RowStatus.INVALID;
        } else if (notices.stream().anyMatch(notice -> "WARNING".equals(notice.getLevel()))) {
            status = RowStatus.WARNING;
        } else {
            status = RowStatus.VALID;
        }
        return new ProcessedRow(
                status,
                kind,
                snapshot,
                errors.isEmpty() ? propertyRequest : null,
                errors.isEmpty() ? messRequest : null,
                List.copyOf(errors),
                List.copyOf(notices),
                Map.copyOf(normalizedValues));
    }

    private static void remember(
            Map<String, String> normalizedValues,
            PropertyBulkImportField field,
            boolean normalized,
            String label) {
        if (normalized && label != null && !label.isBlank()) {
            normalizedValues.put(field.name(), label);
        }
    }

    private static PropertyBulkImportFieldNotice notice(
            PropertyBulkImportField field,
            String level,
            String message,
            String original,
            String normalized) {
        return PropertyBulkImportFieldNotice.builder()
                .field(field.name())
                .level(level)
                .message(message)
                .originalValue(original)
                .normalizedValue(normalized)
                .build();
    }

    private static void addFoodAmenityWarnings(
            Map<PropertyBulkImportField, String> rawByField,
            FoodIncludedNormalizer.Result foodResult,
            List<PropertyBulkImportFieldNotice> notices) {
        if (foodResult.value() == null || foodResult.error() != null) {
            return;
        }
        String amenities = PropertyBulkImportValueParser.blankToNull(
                rawByField.get(PropertyBulkImportField.AMENITIES));
        if (amenities == null) {
            return;
        }
        String key = PropertyBulkImportValueParser.classificationKey(amenities);
        if (Boolean.FALSE.equals(foodResult.value())
                && (key.contains("meals included") || key.contains("food included"))) {
            notices.add(notice(
                    PropertyBulkImportField.FOOD_INCLUDED,
                    "WARNING",
                    "Food Included is 'No' but Amenities contains '" + amenities + "'. Please verify.",
                    foodResult.original(),
                    "No"));
        }
        if (Boolean.TRUE.equals(foodResult.value()) && key.contains("no food")) {
            notices.add(notice(
                    PropertyBulkImportField.FOOD_INCLUDED,
                    "WARNING",
                    "Food Included is 'Yes' but Amenities contains '" + amenities + "'. Please verify.",
                    foodResult.original(),
                    "Yes"));
        }
    }

    private static void addBlankWarnings(
            Map<String, String> mapping,
            Map<PropertyBulkImportField, String> rawByField,
            SpaceTypeNormalizer.Result typeResult,
            com.acomi.acomi_backend.space.domain.model.GenderPolicy gender,
            FoodIncludedNormalizer.Result foodResult,
            List<PropertyBulkImportFieldNotice> notices) {
        warnIfMappedBlank(mapping, rawByField, notices, PropertyBulkImportField.PROPERTY_TYPE,
                typeResult.type() == null && typeResult.error() == null,
                "Space type is blank. No default category was assigned.");
        warnIfMappedBlank(mapping, rawByField, notices, PropertyBulkImportField.PROPERTY_NAME,
                PropertyBulkImportValueParser.blankToNull(rawByField.get(PropertyBulkImportField.PROPERTY_NAME)) == null,
                "Property name is blank. No name was generated.");
        warnIfMappedBlank(mapping, rawByField, notices, PropertyBulkImportField.MOBILE_NUMBER,
                PropertyBulkImportValueParser.blankToNull(rawByField.get(PropertyBulkImportField.MOBILE_NUMBER)) == null,
                "Contact 1 is blank. No placeholder number was generated.");
        warnIfMappedBlank(mapping, rawByField, notices, PropertyBulkImportField.GENDER,
                gender == null
                        && PropertyBulkImportValueParser.blankToNull(
                                rawByField.get(PropertyBulkImportField.GENDER)) == null,
                "Gender is blank. It was not set to Both.");
        warnIfMappedBlank(mapping, rawByField, notices, PropertyBulkImportField.STARTING_PRICE,
                PropertyBulkImportValueParser.blankToNull(rawByField.get(PropertyBulkImportField.STARTING_PRICE)) == null,
                "Rent starts from is blank. Rent was not inferred or set to zero.");
        warnIfMappedBlank(mapping, rawByField, notices, PropertyBulkImportField.FOOD_INCLUDED,
                foodResult.value() == null && foodResult.warning() == null && foodResult.error() == null,
                "Food included is blank. Food status remains unknown.");
        warnIfMappedBlank(mapping, rawByField, notices, PropertyBulkImportField.ADDRESS_LINE,
                PropertyBulkImportValueParser.blankToNull(rawByField.get(PropertyBulkImportField.ADDRESS_LINE)) == null,
                "Address given is blank. No address was generated.");
        warnIfMappedBlank(mapping, rawByField, notices, PropertyBulkImportField.MAP_URL,
                PropertyBulkImportValueParser.blankToNull(rawByField.get(PropertyBulkImportField.MAP_URL)) == null,
                "Google Maps Link is blank. No map URL was generated.");
    }

    private static void warnIfMappedBlank(
            Map<String, String> mapping,
            Map<PropertyBulkImportField, String> rawByField,
            List<PropertyBulkImportFieldNotice> notices,
            PropertyBulkImportField field,
            boolean blank,
            String message) {
        if (mapping == null || !mapping.containsKey(field.name()) || !blank) {
            return;
        }
        String header = mapping.get(field.name());
        if (header == null || header.isBlank()) {
            return;
        }
        notices.add(notice(field, "WARNING", message, rawByField.get(field), null));
    }

    private static SharedParsedFields parseSharedFields(
            Map<PropertyBulkImportField, String> rawByField,
            List<PropertyBulkImportFieldError> errors,
            List<PropertyBulkImportFieldNotice> notices,
            com.acomi.acomi_backend.space.domain.model.GenderPolicy gender,
            Boolean foodIncluded) {
        String ownerName = PropertyBulkImportValueParser.blankToNull(
                rawByField.get(PropertyBulkImportField.OWNER_NAME));

        List<String> extraNotes = new ArrayList<>();
        String mobile = applyContact(
                rawByField.get(PropertyBulkImportField.MOBILE_NUMBER),
                PropertyBulkImportField.MOBILE_NUMBER,
                "Contact 1",
                errors,
                extraNotes);
        String altMobile = applyContact(
                rawByField.get(PropertyBulkImportField.ALTERNATE_MOBILE_NUMBER),
                PropertyBulkImportField.ALTERNATE_MOBILE_NUMBER,
                "Contact 2",
                errors,
                extraNotes);
        String contact3 = applyContact(
                rawByField.get(PropertyBulkImportField.CONTACT_3),
                PropertyBulkImportField.CONTACT_3,
                "Contact 3",
                errors,
                extraNotes);

        String addressLine = PropertyBulkImportValueParser.blankToNull(
                rawByField.get(PropertyBulkImportField.ADDRESS_LINE));
        String city = PropertyBulkImportValueParser.blankToNull(rawByField.get(PropertyBulkImportField.CITY));
        String state = PropertyBulkImportValueParser.blankToNull(rawByField.get(PropertyBulkImportField.STATE));

        String pincode = PropertyBulkImportValueParser.blankToNull(
                rawByField.get(PropertyBulkImportField.PINCODE));
        if (pincode != null) {
            String pinDigits = pincode.replaceAll("\\D", "");
            pincode = pinDigits.isEmpty() ? pincode : pinDigits;
        }

        String mapUrl = PropertyBulkImportValueParser.blankToNull(
                rawByField.get(PropertyBulkImportField.MAP_URL));
        if (mapUrl != null && !com.acomi.acomi_backend.space.application.service.ListingMapLocation.looksLikeHttpUrl(mapUrl)) {
            errors.add(new PropertyBulkImportFieldError(
                    PropertyBulkImportField.MAP_URL.name(),
                    "Map link must start with http:// or https:// (got: " + mapUrl + ")"));
            mapUrl = null;
        }

        var priceResult = PropertyBulkImportValueParser.parseStartingPrice(
                rawByField.get(PropertyBulkImportField.STARTING_PRICE));
        if (priceResult.isInvalid()) {
            errors.add(new PropertyBulkImportFieldError(
                    PropertyBulkImportField.STARTING_PRICE.name(), priceResult.error()));
        } else if (priceResult.warning() != null) {
            notices.add(notice(
                    PropertyBulkImportField.STARTING_PRICE,
                    "WARNING",
                    priceResult.warning(),
                    rawByField.get(PropertyBulkImportField.STARTING_PRICE),
                    null));
        }

        var boolResult = PropertyBulkImportValueParser.parseBoolean(
                rawByField.get(PropertyBulkImportField.TEST_LEAD));
        if (boolResult.isInvalid()) {
            errors.add(new PropertyBulkImportFieldError(
                    PropertyBulkImportField.TEST_LEAD.name(), boolResult.error()));
        }

        var latResult = PropertyBulkImportValueParser.parseCoordinate(
                rawByField.get(PropertyBulkImportField.LATITUDE), "Latitude");
        if (latResult.isInvalid()) {
            errors.add(new PropertyBulkImportFieldError(
                    PropertyBulkImportField.LATITUDE.name(), latResult.error()));
        }

        var lngResult = PropertyBulkImportValueParser.parseCoordinate(
                rawByField.get(PropertyBulkImportField.LONGITUDE), "Longitude");
        if (lngResult.isInvalid()) {
            errors.add(new PropertyBulkImportFieldError(
                    PropertyBulkImportField.LONGITUDE.name(), lngResult.error()));
        }

        String sharingNotes = PropertyBulkImportValueParser.normalizeFreeText(
                rawByField.get(PropertyBulkImportField.SHARING));
        if (sharingNotes != null && sharingNotes.matches("(?i).*(\\p{Sc}|₹|\\brs\\.?\\s*\\d|\\d[\\d,]*\\s*/\\s*(bed|month|night|order|day)).*")) {
            notices.add(notice(
                    PropertyBulkImportField.SHARING,
                    "WARNING",
                    "Sharing contains a price. The text was kept and was not used as rent.",
                    sharingNotes,
                    sharingNotes));
        }
        if (latResult.value() != null && lngResult.value() != null
                && latResult.value().abs().compareTo(new java.math.BigDecimal("60")) > 0
                && lngResult.value().abs().compareTo(new java.math.BigDecimal("60")) <= 0) {
            notices.add(notice(
                    PropertyBulkImportField.LATITUDE,
                    "WARNING",
                    "Latitude and longitude may be swapped. Please verify.",
                    rawByField.get(PropertyBulkImportField.LATITUDE),
                    latResult.value().toPlainString()));
        }
        if (mobile != null && (mobile.equals(altMobile) || mobile.equals(contact3) || (altMobile != null && altMobile.equals(contact3)))) {
            notices.add(notice(
                    PropertyBulkImportField.MOBILE_NUMBER,
                    "WARNING",
                    "The same contact number is repeated on this row.",
                    mobile,
                    mobile));
        }

        var amenitiesResult = PropertyBulkImportValueParser.parseAmenities(
                rawByField.get(PropertyBulkImportField.AMENITIES));

        String unmapped = amenitiesResult.unmappedAmenities();
        for (String note : extraNotes) {
            unmapped = PropertyBulkImportValueParser.mergeUnmappedNotes(unmapped, note);
        }

        return new SharedParsedFields(
                ownerName,
                mobile,
                altMobile,
                contact3,
                addressLine,
                city,
                state,
                pincode,
                mapUrl,
                priceResult.value(),
                priceResult.basis(),
                priceResult.useTypeDefault(),
                boolResult.value(),
                gender,
                foodIncluded,
                latResult.value(),
                lngResult.value(),
                sharingNotes,
                amenitiesResult.amenities(),
                unmapped);
    }

    /**
     * Mobiles go on the contact DTO field. Landlines cannot (Bean Validation is still
     * {@code ^[6-9]\\d{9}$}); they are preserved as notes so the row stays VALID.
     */
    private static String applyContact(
            String raw,
            PropertyBulkImportField field,
            String label,
            List<PropertyBulkImportFieldError> errors,
            List<String> extraNotes) {
        var parsed = PropertyBulkImportValueParser.parseContactPhone(raw);
        if (parsed.isBlank()) {
            return null;
        }
        if (parsed.isInvalid()) {
            errors.add(new PropertyBulkImportFieldError(field.name(), parsed.error()));
            return null;
        }
        if (parsed.kind() == PropertyBulkImportValueParser.PhoneKind.LANDLINE) {
            extraNotes.add(label + " landline: " + parsed.value());
            return null;
        }
        return parsed.value();
    }

    private static void applySharedToProperty(
            AdminCreatePropertyRegistrationRequest request, SharedParsedFields shared) {
        request.setOwnerName(shared.ownerName());
        request.setMobileNumber(shared.mobile());
        request.setAlternateMobileNumber(shared.altMobile());
        request.setAdditionalMobileNumber(shared.contact3());
        request.setAddressLine(shared.addressLine());
        request.setCity(shared.city());
        request.setState(shared.state());
        request.setPincode(shared.pincode());
        request.setMapUrl(shared.mapUrl());
        request.setTestLead(shared.testLead());
        request.setGenderPolicy(shared.genderPolicy());
        request.setFoodIncludedListing(shared.foodIncludedListing());
        request.setLatitude(shared.latitude());
        request.setLongitude(shared.longitude());
        request.setSharingNotes(shared.sharingNotes());
        request.setUnmappedAmenities(shared.unmappedAmenities());
    }

    private static void applySharedToMess(
            AdminCreateMessRegistrationRequest request, SharedParsedFields shared) {
        request.setOwnerName(shared.ownerName());
        request.setMobileNumber(shared.mobile());
        request.setAlternateMobileNumber(shared.altMobile());
        request.setAdditionalMobileNumber(shared.contact3());
        request.setAddressLine(shared.addressLine());
        request.setCity(shared.city());
        request.setState(shared.state());
        request.setPincode(shared.pincode());
        request.setMapUrl(shared.mapUrl());
        request.setTestLead(shared.testLead());
        request.setGenderPolicy(shared.genderPolicy());
        request.setFoodIncludedListing(shared.foodIncludedListing());
        request.setLatitude(shared.latitude());
        request.setLongitude(shared.longitude());
        request.setSharingNotes(shared.sharingNotes());
        request.setUnmappedAmenities(shared.unmappedAmenities());
    }

    private static <T> void addViolations(
            Set<ConstraintViolation<T>> violations, List<PropertyBulkImportFieldError> errors) {
        for (ConstraintViolation<T> v : violations) {
            String fieldPath = v.getPropertyPath() == null ? null : v.getPropertyPath().toString();
            String fieldKey = toFieldKey(fieldPath);
            boolean already =
                    errors.stream()
                            .anyMatch(e -> e.getField().equals(fieldKey)
                                    && e.getMessage().equals(v.getMessage()));
            if (!already) {
                errors.add(new PropertyBulkImportFieldError(fieldKey, v.getMessage()));
            }
        }
    }

    public static boolean isCompletelyBlank(Map<PropertyBulkImportField, String> rawByField) {
        for (PropertyBulkImportField field : PropertyBulkImportField.values()) {
            if (PropertyBulkImportValueParser.blankToNull(rawByField.get(field)) != null) {
                return false;
            }
        }
        return true;
    }

    private static void applyFieldOverrides(
            Map<PropertyBulkImportField, String> rawByField, Map<String, String> fieldOverrides) {
        if (fieldOverrides == null || fieldOverrides.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : fieldOverrides.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            try {
                rawByField.put(PropertyBulkImportField.valueOf(entry.getKey().trim()), entry.getValue());
            } catch (IllegalArgumentException ignored) {
                // Ignore unknown field keys from the client.
            }
        }
    }

    public static Map<PropertyBulkImportField, String> extractMappedValues(
            Map<String, String> excelRow, Map<String, String> mapping) {
        Map<PropertyBulkImportField, String> rawByField = new EnumMap<>(PropertyBulkImportField.class);
        for (PropertyBulkImportField field : PropertyBulkImportField.values()) {
            String header = mapping == null ? null : mapping.get(field.name());
            String value = null;
            if (header != null && !header.isBlank() && excelRow != null) {
                value = excelRow.get(header);
                if (value == null) {
                    for (Map.Entry<String, String> e : excelRow.entrySet()) {
                        if (e.getKey() != null && e.getKey().equalsIgnoreCase(header.trim())) {
                            value = e.getValue();
                            break;
                        }
                    }
                }
            }
            rawByField.put(field, value);
        }
        return rawByField;
    }

    private static String toFieldKey(String propertyPath) {
        if (propertyPath == null || propertyPath.isBlank()) {
            return "UNKNOWN";
        }
        return switch (propertyPath) {
            case "propertyType" -> PropertyBulkImportField.PROPERTY_TYPE.name();
            case "propertyName", "messName" -> PropertyBulkImportField.PROPERTY_NAME.name();
            case "ownerName" -> PropertyBulkImportField.OWNER_NAME.name();
            case "mobileNumber" -> PropertyBulkImportField.MOBILE_NUMBER.name();
            case "alternateMobileNumber" -> PropertyBulkImportField.ALTERNATE_MOBILE_NUMBER.name();
            case "additionalMobileNumber" -> PropertyBulkImportField.CONTACT_3.name();
            case "addressLine" -> PropertyBulkImportField.ADDRESS_LINE.name();
            case "city" -> PropertyBulkImportField.CITY.name();
            case "state" -> PropertyBulkImportField.STATE.name();
            case "pincode" -> PropertyBulkImportField.PINCODE.name();
            case "mapUrl" -> PropertyBulkImportField.MAP_URL.name();
            case "startingPrice", "monthlyPrice" -> PropertyBulkImportField.STARTING_PRICE.name();
            case "testLead" -> PropertyBulkImportField.TEST_LEAD.name();
            case "genderPolicy" -> PropertyBulkImportField.GENDER.name();
            case "foodIncludedListing" -> PropertyBulkImportField.FOOD_INCLUDED.name();
            case "latitude" -> PropertyBulkImportField.LATITUDE.name();
            case "longitude" -> PropertyBulkImportField.LONGITUDE.name();
            case "sharingNotes" -> PropertyBulkImportField.SHARING.name();
            case "unmappedAmenities" -> PropertyBulkImportField.AMENITIES.name();
            default -> propertyPath;
        };
    }

    private record SharedParsedFields(
            String ownerName,
            String mobile,
            String altMobile,
            String contact3,
            String addressLine,
            String city,
            String state,
            String pincode,
            String mapUrl,
            java.math.BigDecimal startingPrice,
            com.acomi.acomi_backend.property.domain.model.PriceBasis priceBasis,
            boolean useTypeDefault,
            Boolean testLead,
            com.acomi.acomi_backend.space.domain.model.GenderPolicy genderPolicy,
            Boolean foodIncludedListing,
            java.math.BigDecimal latitude,
            java.math.BigDecimal longitude,
            String sharingNotes,
            List<com.acomi.acomi_backend.space.api.dto.AmenityAssignmentDto> amenities,
            String unmappedAmenities) {}
}
