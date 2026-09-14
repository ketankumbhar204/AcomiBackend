package com.acomi.acomi_backend.admin.bulkimport.property;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.space.domain.model.SpaceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PropertyBulkImportValueParserTest {

    @ParameterizedTest
    @CsvSource({
        "PG, PG",
        "pg, PG",
        "Hostel, HOSTEL",
        "HOSTEL, HOSTEL",
        "Co-living, CO_LIVING",
        "Co living, CO_LIVING",
        "CO_LIVING, CO_LIVING",
        "Rental, RENTAL",
        "RENTAL, RENTAL"
    })
    void normalizePropertyType_knownLabels(String raw, SpaceType expected) {
        var result = PropertyBulkImportValueParser.parsePropertyType(raw);
        assertThat(result.isInvalid()).isFalse();
        assertThat(result.value()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Villa", "xyz", "bungalow"})
    void normalizePropertyType_unknown_invalid(String raw) {
        var result = PropertyBulkImportValueParser.parsePropertyType(raw);
        assertThat(result.isInvalid()).isTrue();
        assertThat(result.error()).isNotBlank();
    }

    @ParameterizedTest
    @CsvSource({
        "MESS, MESS",
        "mess, MESS",
        "Canteen, MESS"
    })
    void normalizePropertyType_mess_isValidForRouting(String raw, SpaceType expected) {
        var result = PropertyBulkImportValueParser.parsePropertyType(raw);
        assertThat(result.isInvalid()).isFalse();
        assertThat(result.value()).isEqualTo(expected);
    }

    @Test
    void normalizePropertyType_blank_returnsNull() {
        var result = PropertyBulkImportValueParser.parsePropertyType("  ");
        assertThat(result.isInvalid()).isFalse();
        assertThat(result.value()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
        "true, true",
        "FALSE, false",
        "Yes, true",
        "no, false",
        "Y, true",
        "n, false",
        "1, true",
        "0, false"
    })
    void parseBoolean_acceptedValues(String raw, boolean expected) {
        var result = PropertyBulkImportValueParser.parseBoolean(raw);
        assertThat(result.isInvalid()).isFalse();
        assertThat(result.value()).isEqualTo(expected);
    }

    @Test
    void parseBoolean_blank_isNull() {
        var result = PropertyBulkImportValueParser.parseBoolean("");
        assertThat(result.isInvalid()).isFalse();
        assertThat(result.value()).isNull();
    }

    @Test
    void parseBoolean_garbage_invalid() {
        var result = PropertyBulkImportValueParser.parseBoolean("maybe");
        assertThat(result.isInvalid()).isTrue();
    }

    @Test
    void stripMobile_keepsTenDigits() {
        assertThat(PropertyBulkImportValueParser.stripMobileDigits("98765-43210"))
                .isEqualTo("9876543210");
    }

    @Test
    void stripMobile_stripsCountryCode91() {
        assertThat(PropertyBulkImportValueParser.stripMobileDigits("919876543210"))
                .isEqualTo("9876543210");
        assertThat(PropertyBulkImportValueParser.stripMobileDigits("+91 98765 43210"))
                .isEqualTo("9876543210");
        assertThat(PropertyBulkImportValueParser.stripMobileDigits("09876543210"))
                .isEqualTo("9876543210");
    }

    @Test
    void stripMobile_blank_isNull() {
        assertThat(PropertyBulkImportValueParser.stripMobileDigits("   ")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-", " - ", "—", "–", "n/a", "NA", "None", ".", "?"})
    void blankToNull_treatsPlaceholdersAsAbsent(String raw) {
        assertThat(PropertyBulkImportValueParser.blankToNull(raw)).isNull();
    }

    @Test
    void placeholderDash_doesNotInvalidateOptionalParsers() {
        assertThat(PropertyBulkImportValueParser.parseStartingPrice("-").isInvalid()).isFalse();
        assertThat(PropertyBulkImportValueParser.parseStartingPrice("-").value()).isNull();
        assertThat(PropertyBulkImportValueParser.parseGender("-").isInvalid()).isFalse();
        assertThat(PropertyBulkImportValueParser.parseGender("-").value()).isNull();
        assertThat(PropertyBulkImportValueParser.parseFoodIncluded("-").isInvalid()).isFalse();
        assertThat(PropertyBulkImportValueParser.parseFoodIncluded("-").value()).isNull();
        assertThat(PropertyBulkImportValueParser.parseCoordinate("-", "Latitude").isInvalid()).isFalse();
        assertThat(PropertyBulkImportValueParser.parseCoordinate("-", "Latitude").value()).isNull();
    }
}
