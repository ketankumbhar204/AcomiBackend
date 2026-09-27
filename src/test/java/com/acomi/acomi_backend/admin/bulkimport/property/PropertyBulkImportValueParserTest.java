package com.acomi.acomi_backend.admin.bulkimport.property;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
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
        "P.G., PG",
        "PG Hostel, PG",
        "PG / Hostel, PG",
        "Hostel / PG, PG",
        "PG/Hostel, PG",
        "Hostel/PG, PG",
        "PG Services, PG",
        "Paying Guest, PG",
        "Paying Guest Accommodation, PG",
        "Hostel, HOSTEL",
        "HOSTEL, HOSTEL",
        "Hostels, HOSTEL",
        "Boys Hostel, HOSTEL",
        "Girls Hostel, HOSTEL",
        "Boys Hostel / PG, PG",
        "Co-living, CO_LIVING",
        "Co living, CO_LIVING",
        "Coliving, CO_LIVING",
        "CO_LIVING, CO_LIVING",
        "co living space, CO_LIVING",
        "co living accommodation, CO_LIVING",
        "Co-living / PG, CO_LIVING",
        "PG / Co-living, CO_LIVING",
        "Rental, RENTAL",
        "RENTAL, RENTAL",
        "Rental Flat, RENTAL",
        "Rental Flats, RENTAL",
        "Apartment, RENTAL",
        "Apartments, RENTAL",
        "Flat, RENTAL",
        "Flats, RENTAL",
        "Serviced Apartment, RENTAL",
        "Serviced Apartments, RENTAL",
        "Serviced / Corporate Apartment, RENTAL",
        "Corporate Apartment, RENTAL",
        "Serviced Accommodation, RENTAL",
        "MESS, MESS",
        "mess, MESS",
        "Canteen, MESS",
        "Mess / Tiffin, MESS",
        "Mess or Tiffin, MESS",
        "Mess/Tiffin, MESS",
        "Tiffin, MESS",
        "Tiffin Service, MESS",
        "Tiffin Services, MESS",
        "Meal Service, MESS",
        "Meal Services, MESS",
        "Mess / Tiffin Service, MESS",
        "Catering / Food Service, MESS",
        "Catering / Tiffin, MESS",
        "Mess / Catering, MESS",
        "Mess / Food Service, MESS",
        "Tiffin / Food Service, MESS",
        "Guest House, RENTAL",
        "Guest House / Accommodation, RENTAL",
        "Guest House / Stay, RENTAL",
        "PG / Guest House, PG",
        "Guest House / PG, PG",
        "Hostel / Guest House, HOSTEL",
        "Serviced Stay / Guest House, RENTAL",
        "Rental / Guest House, RENTAL",
        "PG / Food Service, PG",
        "PG / Catering, PG",
        "Hostel / Food Service, HOSTEL",
        "Hostel / Catering, HOSTEL",
        "Co-living / Food Service, CO_LIVING"
    })
    void normalizePropertyType_knownLabels(String raw, SpaceType expected) {
        var result = PropertyBulkImportValueParser.parsePropertyType(raw);
        assertThat(result.isInvalid()).isFalse();
        assertThat(result.value()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Villa", "xyz", "bungalow", "unknown value", "Hotel"})
    void normalizePropertyType_unknown_invalid(String raw) {
        var result = PropertyBulkImportValueParser.parsePropertyType(raw);
        assertThat(result.isInvalid()).isTrue();
        assertThat(result.error()).startsWith("Unknown property type:");
        assertThat(result.error()).contains(raw);
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
    void normalizePropertyType_compoundPriority() {
        assertThat(PropertyBulkImportValueParser.parsePropertyType("Co-living / Hostel").value())
                .isEqualTo(SpaceType.CO_LIVING);
        assertThat(PropertyBulkImportValueParser.parsePropertyType("PG / Mess").value())
                .isEqualTo(SpaceType.MESS);
        assertThat(PropertyBulkImportValueParser.parsePropertyType("Hostel / Serviced Apartment").value())
                .isEqualTo(SpaceType.RENTAL);
        assertThat(PropertyBulkImportValueParser.parsePropertyType("PG / Hostel").value())
                .isEqualTo(SpaceType.PG);
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

    @ParameterizedTest
    @CsvSource({
        "+91 91686 91199, 9168691199",
        "91 91686 91199, 9168691199",
        "919168691199, 9168691199",
        "9168691199, 9168691199",
        "+91-91686-91199, 9168691199",
        "+91 91686-91199, 9168691199",
        "09168691199, 9168691199",
        "98765-43210, 9876543210",
        "919876543210, 9876543210",
        "+91 98765 43210, 9876543210",
        "09876543210, 9876543210"
    })
    void parseContactPhone_mobiles(String raw, String expected) {
        var result = PropertyBulkImportValueParser.parseContactPhone(raw);
        assertThat(result.isInvalid()).isFalse();
        assertThat(result.kind()).isEqualTo(PropertyBulkImportValueParser.PhoneKind.MOBILE);
        assertThat(result.value()).isEqualTo(expected);
        assertThat(PropertyBulkImportValueParser.stripMobileDigits(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "020-65328521, 02065328521",
        "020 25807000, 02025807000",
        "020-25807000, 02025807000",
        "'+91 124 620 1217', 01246201217",
        "'+91 20 2611 3701', 02026113701",
        "'+91 020 22932507', 02022932507"
    })
    void parseContactPhone_landlines(String raw, String expected) {
        var result = PropertyBulkImportValueParser.parseContactPhone(raw);
        assertThat(result.isInvalid()).isFalse();
        assertThat(result.kind()).isEqualTo(PropertyBulkImportValueParser.PhoneKind.LANDLINE);
        assertThat(result.value()).isEqualTo(expected);
    }

    @Test
    void parseContactPhone_invalidText() {
        var result = PropertyBulkImportValueParser.parseContactPhone("not-a-phone");
        assertThat(result.isInvalid()).isTrue();
        assertThat(result.error()).startsWith("Invalid contact number:");
    }

    @Test
    void parseContactPhone_tollFreeRemainsInvalid() {
        var result = PropertyBulkImportValueParser.parseContactPhone("+91 1800 266 6654");
        assertThat(result.isInvalid()).isTrue();
        assertThat(result.error()).startsWith("Invalid contact number:");
        assertThat(result.kind()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-", "  ", ""})
    void parseContactPhone_blank(String raw) {
        assertThat(PropertyBulkImportValueParser.parseContactPhone(raw).isBlank()).isTrue();
        assertThat(PropertyBulkImportValueParser.stripMobileDigits(raw)).isNull();
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
    @CsvSource({
        "'₹7,607', 7607",
        "'₹7,500', 7500",
        "'18,000/bed', 18000",
        "'竄ｹ18,000/bed', 18000",
        "'₹8,000', 8000",
        "'₹3,000*', 3000",
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
        "'₹8000', 8000",
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
        assertThat(result.error()).contains(raw);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ask for price", "Negotiable", "Contact owner", "Call for price"})
    void parseStartingPrice_invalidText(String raw) {
        var result = PropertyBulkImportValueParser.parseStartingPrice(raw);
        assertThat(result.isInvalid()).isTrue();
        assertThat(result.error()).startsWith("Invalid rent value:");
    }

    @ParameterizedTest
    @CsvSource({
        "Gents, MALE",
        "Ladies, FEMALE",
        "Both, MIXED",
        "Unisex, MIXED",
        "Male, MALE",
        "Female, FEMALE",
        "Men, MALE",
        "Women, FEMALE",
        "Boys, MALE",
        "Girls, FEMALE",
        "Mixed, MIXED",
        "Mixed Gender, MIXED"
    })
    void parseGender_aliases(String raw, GenderPolicy expected) {
        var result = PropertyBulkImportValueParser.parseGender(raw);
        assertThat(result.isInvalid()).isFalse();
        assertThat(result.value()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "Yes, true, ''",
        "No, false, ''",
        "Y, true, ''",
        "N, false, ''",
        "Included, true, ''",
        "Food included, true, ''",
        "with food, true, ''",
        "Included in rent, true, Included in rent",
        "Breakfast included, true, Breakfast included",
        "Meals included, true, Meals included",
        "Meal included, true, Meal included",
        "Tiffin included, true, Tiffin included",
        "not included, false, ''",
        "without food, false, ''",
        "excluded, false, ''",
        "Food not included, false, Food not included",
        "Extra charge, false, Extra charge",
        "Food charge extra, false, Food charge extra",
        "Optional / separate plan, false, Optional / separate plan",
        "no forced mess fee, false, no forced mess fee",
        "Paid separately, false, Paid separately",
        "Not included in rent, false, Not included in rent"
    })
    void parseFoodIncluded_aliases(String raw, boolean expected, String note) {
        var result = PropertyBulkImportValueParser.parseFoodIncluded(raw);
        assertThat(result.isInvalid()).isFalse();
        assertThat(result.value()).isEqualTo(expected);
        if (note == null || note.isBlank()) {
            assertThat(result.note()).isNull();
        } else {
            assertThat(result.note()).isEqualTo(note);
        }
    }

    @Test
    void parseFoodIncluded_doesNotTreatExtraChargeAsYes() {
        var result = PropertyBulkImportValueParser.parseFoodIncluded("Extra charge");
        assertThat(result.value()).isFalse();
        assertThat(result.note()).isEqualTo("Extra charge");
    }

    @Test
    void normalizeFreeText_preservesSharingLists() {
        assertThat(PropertyBulkImportValueParser.normalizeFreeText("  Single / Twin / Triple  "))
                .isEqualTo("Single / Twin / Triple");
        assertThat(PropertyBulkImportValueParser.normalizeFreeText("2 / 3 Sharing"))
                .isEqualTo("2 / 3 Sharing");
        assertThat(PropertyBulkImportValueParser.normalizeFreeText("-")).isNull();
    }

    @Test
    void parseAmenities_dedupesAndKeepsUnknown() {
        var result = PropertyBulkImportValueParser.parseAmenities("WiFi, wifi, Tiffin service");
        assertThat(result.amenities()).hasSize(1);
        assertThat(result.amenities().get(0).getCode()).isEqualTo("WIFI");
        assertThat(result.unmappedAmenities()).isEqualTo("Tiffin service");
    }

    @Test
    void mapUrl_preservesFullHttpsSearchUrl() {
        String url = "https://www.google.com/maps/search/?api=1&query=D+NEST+PG+HOSTEL,+Aundh,+Pune";
        assertThat(PropertyBulkImportValueParser.blankToNull(url)).isEqualTo(url);
        assertThat(PropertyBulkImportValueParser.blankToNull("-")).isNull();
        assertThat(PropertyBulkImportValueParser.blankToNull("")).isNull();
    }

    @Test
    void parseCoordinate_trimsAndAllowsBlank() {
        assertThat(PropertyBulkImportValueParser.parseCoordinate("  18.5580  ", "Latitude").value())
                .isEqualByComparingTo(new BigDecimal("18.5580"));
        assertThat(PropertyBulkImportValueParser.parseCoordinate("-", "Longitude").value()).isNull();
        assertThat(PropertyBulkImportValueParser.parseCoordinate("200", "Latitude").isInvalid()).isTrue();
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
