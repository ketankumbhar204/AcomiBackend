package com.acomi.acomi_backend.admin.bulkimport.property;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PropertyBulkImportRowProcessorTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void blankRow_whenAllMappedFieldsEmpty() {
        Map<String, String> excelRow = new LinkedHashMap<>();
        excelRow.put("Property Name", "  ");
        excelRow.put("Mobile", "");
        excelRow.put("City", null);

        Map<String, String> mapping = identityMapping(
                "PROPERTY_NAME", "Property Name",
                "MOBILE_NUMBER", "Mobile",
                "CITY", "City");

        var processed = PropertyBulkImportRowProcessor.process(excelRow, mapping, validator);
        assertThat(processed.status()).isEqualTo(PropertyBulkImportRowProcessor.RowStatus.BLANK);
        assertThat(processed.request()).isNull();
    }

    @Test
    void isCompletelyBlank_detectsAnyNonEmptyMappedField() {
        Map<PropertyBulkImportField, String> raw = new EnumMap<>(PropertyBulkImportField.class);
        for (PropertyBulkImportField field : PropertyBulkImportField.values()) {
            raw.put(field, null);
        }
        assertThat(PropertyBulkImportRowProcessor.isCompletelyBlank(raw)).isTrue();
        raw.put(PropertyBulkImportField.CITY, "Pune");
        assertThat(PropertyBulkImportRowProcessor.isCompletelyBlank(raw)).isFalse();
    }

    @Test
    void validRow_buildsRequest() {
        Map<String, String> excelRow = new LinkedHashMap<>();
        excelRow.put("Property Type", "Hostel");
        excelRow.put("Property Name", "Green Hostel");
        excelRow.put("Mobile", "919876543210");
        excelRow.put("Pincode", "560001");
        excelRow.put("Test Lead", "yes");

        Map<String, String> mapping = identityMapping(
                "PROPERTY_TYPE", "Property Type",
                "PROPERTY_NAME", "Property Name",
                "MOBILE_NUMBER", "Mobile",
                "PINCODE", "Pincode",
                "TEST_LEAD", "Test Lead");

        var processed = PropertyBulkImportRowProcessor.process(excelRow, mapping, validator);
        assertThat(processed.status()).isEqualTo(PropertyBulkImportRowProcessor.RowStatus.VALID);
        assertThat(processed.request().getPropertyName()).isEqualTo("Green Hostel");
        assertThat(processed.request().getMobileNumber()).isEqualTo("9876543210");
        assertThat(processed.request().getTestLead()).isTrue();
    }

    @Test
    void messType_routesToMessRequest() {
        Map<String, String> excelRow = Map.of("Type", "MESS", "Property Name", "Campus Mess");
        Map<String, String> mapping = identityMapping(
                "PROPERTY_TYPE", "Type",
                "PROPERTY_NAME", "Property Name");

        var processed = PropertyBulkImportRowProcessor.process(excelRow, mapping, validator);
        assertThat(processed.status()).isEqualTo(PropertyBulkImportRowProcessor.RowStatus.VALID);
        assertThat(processed.targetKind()).isEqualTo(PropertyBulkImportRowProcessor.TargetKind.MESS);
        assertThat(processed.messRequest().getMessName()).isEqualTo("Campus Mess");
        assertThat(processed.propertyRequest()).isNull();
    }

    @Test
    void contact3_preservedOnPropertyRequest() {
        Map<String, String> excelRow = new LinkedHashMap<>();
        excelRow.put("Property Type", "PG");
        excelRow.put("Property Name", "Sunrise");
        excelRow.put("Contact 3", "9123456780");

        Map<String, String> mapping = identityMapping(
                "PROPERTY_TYPE", "Property Type",
                "PROPERTY_NAME", "Property Name",
                "CONTACT_3", "Contact 3");

        var processed = PropertyBulkImportRowProcessor.process(excelRow, mapping, validator);
        assertThat(processed.status()).isEqualTo(PropertyBulkImportRowProcessor.RowStatus.VALID);
        assertThat(processed.request().getAdditionalMobileNumber()).isEqualTo("9123456780");
    }

    @Test
    void invalidPropertyType_marksInvalid() {
        Map<String, String> excelRow = Map.of("Type", "Villa");
        Map<String, String> mapping = identityMapping("PROPERTY_TYPE", "Type");

        var processed = PropertyBulkImportRowProcessor.process(excelRow, mapping, validator);
        assertThat(processed.status()).isEqualTo(PropertyBulkImportRowProcessor.RowStatus.INVALID);
        assertThat(processed.errors()).anyMatch(e -> "PROPERTY_TYPE".equals(e.getField()));
    }

    @Test
    void markAsTestLead_forcesTestLeadTrue() {
        Map<String, String> excelRow = new LinkedHashMap<>();
        excelRow.put("Property Type", "PG");
        excelRow.put("Property Name", "Demo PG");
        excelRow.put("Mobile", "9876543210");

        Map<String, String> mapping = identityMapping(
                "PROPERTY_TYPE", "Property Type",
                "PROPERTY_NAME", "Property Name",
                "MOBILE_NUMBER", "Mobile");

        var processed = PropertyBulkImportRowProcessor.process(excelRow, mapping, validator, true);
        assertThat(processed.status()).isEqualTo(PropertyBulkImportRowProcessor.RowStatus.VALID);
        assertThat(processed.request().getTestLead()).isTrue();
        assertThat(processed.valuesSnapshot().get("TEST_LEAD")).isEqualTo("true");
    }

    @Test
    void compoundPropertyType_pgHostel_keepsOriginalInSnapshot() {
        Map<String, String> excelRow = new LinkedHashMap<>();
        excelRow.put("Property Type", "PG / Hostel");
        excelRow.put("Property Name", "D Nest");
        excelRow.put("Rent", "₹8,000");

        Map<String, String> mapping = identityMapping(
                "PROPERTY_TYPE", "Property Type",
                "PROPERTY_NAME", "Property Name",
                "STARTING_PRICE", "Rent");

        var processed = PropertyBulkImportRowProcessor.process(excelRow, mapping, validator);
        assertThat(processed.status()).isEqualTo(PropertyBulkImportRowProcessor.RowStatus.VALID);
        assertThat(processed.request().getPropertyType())
                .isEqualTo(com.acomi.acomi_backend.space.domain.model.SpaceType.PG);
        assertThat(processed.request().getStartingPrice()).isEqualByComparingTo("8000");
        assertThat(processed.valuesSnapshot().get("PROPERTY_TYPE")).isEqualTo("PG / Hostel");
        assertThat(processed.valuesSnapshot().get("STARTING_PRICE")).isEqualTo("₹8,000");
    }

    @Test
    void landline_doesNotInvalidateRow_preservedAsNote() {
        Map<String, String> excelRow = new LinkedHashMap<>();
        excelRow.put("Property Type", "Hostel");
        excelRow.put("Property Name", "Campus Hostel");
        excelRow.put("Mobile", "020-65328521");

        Map<String, String> mapping = identityMapping(
                "PROPERTY_TYPE", "Property Type",
                "PROPERTY_NAME", "Property Name",
                "MOBILE_NUMBER", "Mobile");

        var processed = PropertyBulkImportRowProcessor.process(excelRow, mapping, validator);
        assertThat(processed.status()).isEqualTo(PropertyBulkImportRowProcessor.RowStatus.VALID);
        assertThat(processed.request().getMobileNumber()).isNull();
        assertThat(processed.request().getUnmappedAmenities()).contains("02065328521");
        assertThat(processed.valuesSnapshot().get("MOBILE_NUMBER")).isEqualTo("020-65328521");
    }

    @Test
    void plus91StdLandline_isValidNote_previewKeepsOriginal() {
        Map<String, String> excelRow = new LinkedHashMap<>();
        excelRow.put("Property Type", "Serviced Apartment");
        excelRow.put("Property Name", "Capital O Hotel");
        excelRow.put("Mobile", "+91 124 620 1217");

        Map<String, String> mapping = identityMapping(
                "PROPERTY_TYPE", "Property Type",
                "PROPERTY_NAME", "Property Name",
                "MOBILE_NUMBER", "Mobile");

        var processed = PropertyBulkImportRowProcessor.process(excelRow, mapping, validator);
        assertThat(processed.status()).isEqualTo(PropertyBulkImportRowProcessor.RowStatus.VALID);
        assertThat(processed.request().getMobileNumber()).isNull();
        assertThat(processed.request().getUnmappedAmenities()).contains("01246201217");
        assertThat(processed.request().getUnmappedAmenities()).contains("landline");
        assertThat(processed.valuesSnapshot().get("MOBILE_NUMBER")).isEqualTo("+91 124 620 1217");
        assertThat(processed.request().getPropertyType())
                .isEqualTo(com.acomi.acomi_backend.space.domain.model.SpaceType.RENTAL);
    }

    @Test
    void guestHouseAccommodation_isRental_previewKeepsOriginal() {
        Map<String, String> excelRow = new LinkedHashMap<>();
        excelRow.put("Property Type", "Guest House / Accommodation");
        excelRow.put("Property Name", "Pg services");

        Map<String, String> mapping = identityMapping(
                "PROPERTY_TYPE", "Property Type",
                "PROPERTY_NAME", "Property Name");

        var processed = PropertyBulkImportRowProcessor.process(excelRow, mapping, validator);
        assertThat(processed.status()).isEqualTo(PropertyBulkImportRowProcessor.RowStatus.VALID);
        assertThat(processed.request().getPropertyType())
                .isEqualTo(com.acomi.acomi_backend.space.domain.model.SpaceType.RENTAL);
        assertThat(processed.valuesSnapshot().get("PROPERTY_TYPE")).isEqualTo("Guest House / Accommodation");
    }

    @Test
    void foodExtraCharge_isNo_andNoteIsPreserved() {
        Map<String, String> excelRow = new LinkedHashMap<>();
        excelRow.put("Property Type", "PG");
        excelRow.put("Property Name", "Sunrise PG");
        excelRow.put("Food", "Extra charge");

        Map<String, String> mapping = identityMapping(
                "PROPERTY_TYPE", "Property Type",
                "PROPERTY_NAME", "Property Name",
                "FOOD_INCLUDED", "Food");

        var processed = PropertyBulkImportRowProcessor.process(excelRow, mapping, validator);
        assertThat(processed.status()).isEqualTo(PropertyBulkImportRowProcessor.RowStatus.VALID);
        assertThat(processed.request().getFoodIncludedListing()).isFalse();
        assertThat(processed.request().getUnmappedAmenities()).contains("Extra charge");
        assertThat(processed.valuesSnapshot().get("FOOD_INCLUDED")).isEqualTo("Extra charge");
    }

    @Test
    void mapUrl_isPreservedUnchanged() {
        String url = "https://www.google.com/maps/search/?api=1&query=D+NEST+PG+HOSTEL";
        Map<String, String> excelRow = new LinkedHashMap<>();
        excelRow.put("Property Type", "PG");
        excelRow.put("Property Name", "D Nest");
        excelRow.put("Google Maps Link", url);

        Map<String, String> mapping = identityMapping(
                "PROPERTY_TYPE", "Property Type",
                "PROPERTY_NAME", "Property Name",
                "MAP_URL", "Google Maps Link");

        var processed = PropertyBulkImportRowProcessor.process(excelRow, mapping, validator);
        assertThat(processed.status()).isEqualTo(PropertyBulkImportRowProcessor.RowStatus.VALID);
        assertThat(processed.request().getMapUrl()).isEqualTo(url);
        assertThat(processed.request().getLatitude()).isNull();
        assertThat(processed.request().getLongitude()).isNull();
        assertThat(processed.valuesSnapshot().get("MAP_URL")).isEqualTo(url);
    }

    @Test
    void sharing_preservesCompoundText() {
        Map<String, String> excelRow = new LinkedHashMap<>();
        excelRow.put("Property Type", "PG");
        excelRow.put("Property Name", "Sunrise");
        excelRow.put("Sharing", "  Single / Twin / Triple  ");

        Map<String, String> mapping = identityMapping(
                "PROPERTY_TYPE", "Property Type",
                "PROPERTY_NAME", "Property Name",
                "SHARING", "Sharing");

        var processed = PropertyBulkImportRowProcessor.process(excelRow, mapping, validator);
        assertThat(processed.status()).isEqualTo(PropertyBulkImportRowProcessor.RowStatus.VALID);
        assertThat(processed.request().getSharingNotes()).isEqualTo("Single / Twin / Triple");
        assertThat(processed.valuesSnapshot().get("SHARING")).isEqualTo("  Single / Twin / Triple  ");
    }

    @Test
    void placeholderOnlyMappedRow_isBlank() {
        Map<String, String> excelRow = new LinkedHashMap<>();
        excelRow.put("Property Type", "-");
        excelRow.put("Property Name", "n/a");
        excelRow.put("Mobile", "—");

        Map<String, String> mapping = identityMapping(
                "PROPERTY_TYPE", "Property Type",
                "PROPERTY_NAME", "Property Name",
                "MOBILE_NUMBER", "Mobile");

        var processed = PropertyBulkImportRowProcessor.process(excelRow, mapping, validator);
        assertThat(processed.status()).isEqualTo(PropertyBulkImportRowProcessor.RowStatus.BLANK);
    }

    @Test
    void rentRange_isFieldInvalid_notSilent() {
        Map<String, String> excelRow = Map.of("Type", "PG", "Rent", "₹5,000-₹7,000");
        Map<String, String> mapping = identityMapping(
                "PROPERTY_TYPE", "Type",
                "STARTING_PRICE", "Rent");

        var processed = PropertyBulkImportRowProcessor.process(excelRow, mapping, validator);
        assertThat(processed.status()).isEqualTo(PropertyBulkImportRowProcessor.RowStatus.INVALID);
        assertThat(processed.errors())
                .anyMatch(e -> "STARTING_PRICE".equals(e.getField())
                        && e.getMessage().contains("Rent range cannot be represented"));
        assertThat(processed.valuesSnapshot().get("STARTING_PRICE")).isEqualTo("₹5,000-₹7,000");
    }

    private static Map<String, String> identityMapping(String... keyHeaderPairs) {
        Map<String, String> mapping = new LinkedHashMap<>();
        for (PropertyBulkImportField field : PropertyBulkImportField.values()) {
            mapping.put(field.name(), null);
        }
        for (int i = 0; i < keyHeaderPairs.length; i += 2) {
            mapping.put(keyHeaderPairs[i], keyHeaderPairs[i + 1]);
        }
        return mapping;
    }
}
