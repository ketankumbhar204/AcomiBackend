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
