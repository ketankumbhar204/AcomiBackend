package com.acomi.acomi_backend.admin.bulkimport.property;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PropertyBulkImportAutoMapperTest {

    @Test
    void suggestMapping_matchesCommonAliases() {
        List<String> headers = List.of(
                "PG Name",
                "Owner Name",
                "Mobile",
                "City",
                "Pin Code",
                "Property Type",
                "Test Lead");

        Map<String, String> suggested = PropertyBulkImportAutoMapper.suggestMapping(headers);

        assertThat(suggested.get("PROPERTY_NAME")).isEqualTo("PG Name");
        assertThat(suggested.get("OWNER_NAME")).isEqualTo("Owner Name");
        assertThat(suggested.get("MOBILE_NUMBER")).isEqualTo("Mobile");
        assertThat(suggested.get("CITY")).isEqualTo("City");
        assertThat(suggested.get("PINCODE")).isEqualTo("Pin Code");
        assertThat(suggested.get("PROPERTY_TYPE")).isEqualTo("Property Type");
        assertThat(suggested.get("TEST_LEAD")).isEqualTo("Test Lead");
        assertThat(suggested.get("ADDRESS_LINE")).isNull();
    }

    @Test
    void suggestMapping_leavesAmbiguousFieldUnmapped() {
        List<String> headers = List.of("Property Name", "PG Name", "City");

        Map<String, String> suggested = PropertyBulkImportAutoMapper.suggestMapping(headers);

        assertThat(suggested.get("PROPERTY_NAME")).isNull();
        assertThat(suggested.get("CITY")).isEqualTo("City");
    }

    @Test
    void suggestMapping_matchesSpreadsheetMapColumns() {
        List<String> headers = List.of(
                "Property Type",
                "Property Name",
                "Contact1",
                "Latitude",
                "Longitude",
                "Google Maps Link");

        Map<String, String> suggested = PropertyBulkImportAutoMapper.suggestMapping(headers);

        assertThat(suggested.get("PROPERTY_TYPE")).isEqualTo("Property Type");
        assertThat(suggested.get("PROPERTY_NAME")).isEqualTo("Property Name");
        assertThat(suggested.get("MOBILE_NUMBER")).isEqualTo("Contact1");
        assertThat(suggested.get("LATITUDE")).isEqualTo("Latitude");
        assertThat(suggested.get("LONGITUDE")).isEqualTo("Longitude");
        assertThat(suggested.get("MAP_URL")).isEqualTo("Google Maps Link");
    }

    @Test
    void suggestMapping_matchesMapUrlHeaderVariants() {
        assertThat(PropertyBulkImportAutoMapper.suggestMapping(List.of("Maps Link")).get("MAP_URL"))
                .isEqualTo("Maps Link");
        assertThat(PropertyBulkImportAutoMapper.suggestMapping(List.of("Map URL")).get("MAP_URL"))
                .isEqualTo("Map URL");
        assertThat(PropertyBulkImportAutoMapper.suggestMapping(List.of("Google Maps URL")).get("MAP_URL"))
                .isEqualTo("Google Maps URL");
        assertThat(PropertyBulkImportAutoMapper.suggestMapping(List.of("Google Map Link")).get("MAP_URL"))
                .isEqualTo("Google Map Link");
    }

    @Test
    void suggestMapping_ignoresBlankHeaders() {
        Map<String, String> suggested =
                PropertyBulkImportAutoMapper.suggestMapping(List.of("", "  ", "State"));
        assertThat(suggested.get("STATE")).isEqualTo("State");
        assertThat(suggested.values().stream().filter(v -> v != null).count()).isEqualTo(1);
    }
}
