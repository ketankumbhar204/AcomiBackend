package com.acomi.acomi_backend.admin.bulkimport.property;

import com.acomi.acomi_backend.admin.bulkimport.property.dto.PropertyBulkImportFieldError;
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
import java.util.regex.Pattern;

/**
 * Maps a raw Excel data row through a field→header mapping into a validated property or mess
 * request (or BLANK / INVALID outcome).
 */
public final class PropertyBulkImportRowProcessor {

    private static final Pattern MOBILE = Pattern.compile("^[6-9]\\d{9}$");

    public enum RowStatus {
        VALID,
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
            List<PropertyBulkImportFieldError> errors) {

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
        Map<PropertyBulkImportField, String> rawByField = extractMappedValues(excelRow, mapping);
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (PropertyBulkImportField field : PropertyBulkImportField.values()) {
            String raw = rawByField.get(field);
            snapshot.put(field.name(), raw == null ? "" : raw);
        }

        if (isCompletelyBlank(rawByField)) {
            return new ProcessedRow(RowStatus.BLANK, null, snapshot, null, null, List.of());
        }

        List<PropertyBulkImportFieldError> errors = new ArrayList<>();
        var typeResult = PropertyBulkImportValueParser.parsePropertyType(
                rawByField.get(PropertyBulkImportField.PROPERTY_TYPE));
        if (typeResult.isInvalid()) {
            errors.add(new PropertyBulkImportFieldError(
                    PropertyBulkImportField.PROPERTY_TYPE.name(), typeResult.error()));
        }

        boolean mess = typeResult.value() == SpaceType.MESS;
        SharedParsedFields shared = parseSharedFields(rawByField, errors);

        if (mess) {
            AdminCreateMessRegistrationRequest request = new AdminCreateMessRegistrationRequest();
            applySharedToMess(request, shared);
            request.setMessName(PropertyBulkImportValueParser.blankToNull(
                    rawByField.get(PropertyBulkImportField.PROPERTY_NAME)));
            if (shared.startingPrice() != null) {
                request.setMonthlyPrice(shared.startingPrice());
            }
            if (markAsTestLead) {
                request.setTestLead(true);
                snapshot.put(PropertyBulkImportField.TEST_LEAD.name(), "true");
            }

            if (validator != null) {
                addViolations(validator.validate(request), errors);
            }
            if (!errors.isEmpty()) {
                return new ProcessedRow(RowStatus.INVALID, TargetKind.MESS, snapshot, null, null, List.copyOf(errors));
            }
            return new ProcessedRow(RowStatus.VALID, TargetKind.MESS, snapshot, null, request, List.of());
        }

        AdminCreatePropertyRegistrationRequest request = new AdminCreatePropertyRegistrationRequest();
        if (typeResult.value() != null) {
            request.setPropertyType(typeResult.value());
        }
        applySharedToProperty(request, shared);
        request.setPropertyName(PropertyBulkImportValueParser.blankToNull(
                rawByField.get(PropertyBulkImportField.PROPERTY_NAME)));
        request.setStartingPrice(shared.startingPrice());
        request.setAmenities(shared.amenities());
        if (markAsTestLead) {
            request.setTestLead(true);
            snapshot.put(PropertyBulkImportField.TEST_LEAD.name(), "true");
        }
        if (validator != null) {
            addViolations(validator.validate(request), errors);
        }
        if (!errors.isEmpty()) {
            return new ProcessedRow(
                    RowStatus.INVALID, TargetKind.PROPERTY, snapshot, null, null, List.copyOf(errors));
        }
        return new ProcessedRow(RowStatus.VALID, TargetKind.PROPERTY, snapshot, request, null, List.of());
    }

    private static SharedParsedFields parseSharedFields(
            Map<PropertyBulkImportField, String> rawByField, List<PropertyBulkImportFieldError> errors) {
        String ownerName = PropertyBulkImportValueParser.blankToNull(
                rawByField.get(PropertyBulkImportField.OWNER_NAME));

        String mobile = PropertyBulkImportValueParser.stripMobileDigits(
                rawByField.get(PropertyBulkImportField.MOBILE_NUMBER));
        if (mobile != null && !MOBILE.matcher(mobile).matches()) {
            errors.add(new PropertyBulkImportFieldError(
                    PropertyBulkImportField.MOBILE_NUMBER.name(),
                    "Mobile number must be a valid 10-digit Indian number"));
            mobile = null;
        }

        String altMobile = PropertyBulkImportValueParser.stripMobileDigits(
                rawByField.get(PropertyBulkImportField.ALTERNATE_MOBILE_NUMBER));
        if (altMobile != null && !MOBILE.matcher(altMobile).matches()) {
            errors.add(new PropertyBulkImportFieldError(
                    PropertyBulkImportField.ALTERNATE_MOBILE_NUMBER.name(),
                    "Alternate mobile number must be a valid 10-digit Indian number"));
            altMobile = null;
        }

        String contact3 = PropertyBulkImportValueParser.stripMobileDigits(
                rawByField.get(PropertyBulkImportField.CONTACT_3));
        if (contact3 != null && !MOBILE.matcher(contact3).matches()) {
            errors.add(new PropertyBulkImportFieldError(
                    PropertyBulkImportField.CONTACT_3.name(),
                    "Contact 3 must be a valid 10-digit Indian number"));
            contact3 = null;
        }

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

        var priceResult = PropertyBulkImportValueParser.parseStartingPrice(
                rawByField.get(PropertyBulkImportField.STARTING_PRICE));
        if (priceResult.isInvalid()) {
            errors.add(new PropertyBulkImportFieldError(
                    PropertyBulkImportField.STARTING_PRICE.name(), priceResult.error()));
        }

        var boolResult = PropertyBulkImportValueParser.parseBoolean(
                rawByField.get(PropertyBulkImportField.TEST_LEAD));
        if (boolResult.isInvalid()) {
            errors.add(new PropertyBulkImportFieldError(
                    PropertyBulkImportField.TEST_LEAD.name(), boolResult.error()));
        }

        var genderResult = PropertyBulkImportValueParser.parseGender(
                rawByField.get(PropertyBulkImportField.GENDER));
        if (genderResult.isInvalid()) {
            errors.add(new PropertyBulkImportFieldError(
                    PropertyBulkImportField.GENDER.name(), genderResult.error()));
        }

        var foodResult = PropertyBulkImportValueParser.parseFoodIncluded(
                rawByField.get(PropertyBulkImportField.FOOD_INCLUDED));
        if (foodResult.isInvalid()) {
            errors.add(new PropertyBulkImportFieldError(
                    PropertyBulkImportField.FOOD_INCLUDED.name(), foodResult.error()));
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

        String sharingNotes = PropertyBulkImportValueParser.blankToNull(
                rawByField.get(PropertyBulkImportField.SHARING));

        var amenitiesResult = PropertyBulkImportValueParser.parseAmenities(
                rawByField.get(PropertyBulkImportField.AMENITIES));

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
                boolResult.value(),
                genderResult.value(),
                foodResult.value(),
                latResult.value(),
                lngResult.value(),
                sharingNotes,
                amenitiesResult.amenities(),
                amenitiesResult.unmappedAmenities());
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
            Boolean testLead,
            com.acomi.acomi_backend.space.domain.model.GenderPolicy genderPolicy,
            Boolean foodIncludedListing,
            java.math.BigDecimal latitude,
            java.math.BigDecimal longitude,
            String sharingNotes,
            List<com.acomi.acomi_backend.space.api.dto.AmenityAssignmentDto> amenities,
            String unmappedAmenities) {}
}
