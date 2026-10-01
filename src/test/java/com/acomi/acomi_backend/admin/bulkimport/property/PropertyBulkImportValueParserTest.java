package com.acomi.acomi_backend.admin.bulkimport.property;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PropertyBulkImportValueParserTest {

    @ParameterizedTest
    @CsvSource({
        "PG, PG",
        "pg, PG",
        "PG / Hostel, PG",
        "Hostel / PG, PG",
        "Hostel, HOSTEL",
        "HOSTEL, HOSTEL",
        "Co-living, CO_LIVING",
        "Co living, CO_LIVING",
        "CO_LIVING, CO_LIVING",
        "Rental, RENTAL",
        "RENTAL, RENTAL",
        "Serviced / Corporate Apartment, RENTAL",
        "Mess or Tiffin, MESS",
        "Mess / Tiffin, MESS",
        "Guest House, RENTAL",
        "PG / Food Service, PG"
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

    @ParameterizedTest
    @CsvSource({
        "'₹7,607', 7607",
        "'₹7,500', 7500",
        "'18,000/bed', 18000",
        "'竄ｹ18,000/bed', 18000",
        "'₹8,000', 8000",
        "'â‚¹10,000', 10000",
        "'竄ｹ8,000/bed', 8000",
        "'₹7,500/bed', 7500",
        "'₹5,000/month', 5000",
        "'₹8,000 per bed', 8000",
        "'₹8,500/person', 8500",
        "'₹2,000/bed*', 2000",
        "'₹8,599/mo', 8599",
        "'₹8,499/mo', 8499",
        "'₹5,300/mo', 5300",
        "'Rs 10,000 per month', 10000",
        "'INR 12,500/bed', 12500",
        "5000, 5000",
        "'5,000', 5000",
        "'Rs. 8000', 8000",
        "'INR 6000', 6000"
    })
    void parseStartingPrice_normalizesCurrencyAndSuffixes(String raw, String expected) {
        var result = PropertyBulkImportValueParser.parseStartingPrice(raw);
        assertThat(result.isInvalid()).isFalse();
        assertThat(result.value()).isEqualByComparingTo(new BigDecimal(expected));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "₹5,000-₹7,000",
                "₹5,000 - ₹7,000",
                "5000-7000",
                "₹8,000 - ₹10,000",
                "₹8,000 to ₹10,000",
                "8000/9000"
            })
    void parseStartingPrice_rejectsRange(String raw) {
        var result = PropertyBulkImportValueParser.parseStartingPrice(raw);
        assertThat(result.isInvalid()).isTrue();
        assertThat(result.error()).contains("Rent range cannot be represented");
    }

    @Test
    void parseFoodIncluded_foodChargeExtra_isNo() {
        var result = PropertyBulkImportValueParser.parseFoodIncluded("Food charge extra");
        assertThat(result.isInvalid()).isFalse();
        assertThat(result.value()).isFalse();
    }

    @Test
    void parseFoodIncluded_optionalSeparatePlan_isNo() {
        var result = PropertyBulkImportValueParser.parseFoodIncluded("Optional / separate plan");
        assertThat(result.isInvalid()).isFalse();
        assertThat(result.value()).isFalse();
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
