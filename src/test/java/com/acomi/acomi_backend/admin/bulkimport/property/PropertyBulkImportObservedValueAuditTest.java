package com.acomi.acomi_backend.admin.bulkimport.property;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.space.application.service.ListingMapLocation;
import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Locks the current parser outcome for values seen in admin import datasets.
 * This records behavior. It does not change it.
 */
class PropertyBulkImportObservedValueAuditTest {

    @Test
    void spaceTypes_matchCurrentNormalizer() {
        acceptType("PG", SpaceType.PG, null, false);
        acceptType("HOSTEL", SpaceType.HOSTEL, null, false);
        acceptType("CO_LIVING", SpaceType.CO_LIVING, null, false);
        acceptType("RENTAL", SpaceType.RENTAL, null, false);
        acceptType("MESS", SpaceType.MESS, null, false);
        reviewTypes("PG / Hostel", SpaceType.PG, SpaceType.HOSTEL);
        reviewTypes("Hostel / PG", SpaceType.PG, SpaceType.HOSTEL);
        acceptType("PG Services", SpaceType.PG, null, false);
        acceptType("Mess / Tiffin", SpaceType.MESS, null, false);
        acceptType("Mess / Tiffin Service", SpaceType.MESS, null, false);
        acceptType("Mess/Tiffin Service", SpaceType.MESS, null, false);
        acceptType("Tiffin / Meal Service", SpaceType.MESS, null, false);
        acceptType("Serviced Apartment", SpaceType.RENTAL, null, false);
        reviewTypes("Serviced / Corporate Apartment", SpaceType.CORPORATE_STAY, SpaceType.RENTAL);
        acceptType("Rental Room", SpaceType.RENTAL, null, false);
        acceptType("Rental Flat / Apartment", SpaceType.RENTAL, null, false);
        acceptType("Rental Flats / Apartments", SpaceType.RENTAL, null, false);
        rejectMixed("PG / Co-living");
        rejectMixed("PG / Apartment");
        rejectMixed("Co-living / PG");
        rejectMixed("Co-living / Hostel");
        rejectMixed("Hostel / Co-living");
        acceptType("Girls PG", SpaceType.PG, null, false);
        assertThat(SpaceTypeNormalizer.normalize("Girls PG").inferredGender()).isEqualTo(GenderPolicy.FEMALE);
        acceptType("Boys PG", SpaceType.PG, null, false);
        assertThat(SpaceTypeNormalizer.normalize("Boys PG").inferredGender()).isEqualTo(GenderPolicy.MALE);
        acceptType("Boys Hostel", SpaceType.HOSTEL, null, false);
        assertThat(SpaceTypeNormalizer.normalize("Boys Hostel").inferredGender()).isEqualTo(GenderPolicy.MALE);
        rejectMixed("Apartment / PG");
        rejectMixed("PG / Room Rental");
        reviewTypes("Villa / Serviced Accommodation", SpaceType.RENTAL);
        blankType("");
        blankType("-");
        blankType("  ");
        acceptType("CORPORATE_STAY", SpaceType.CORPORATE_STAY, null, false);
        acceptType("Corporate Stay", SpaceType.CORPORATE_STAY, null, false);
        acceptType("Corporate stay", SpaceType.CORPORATE_STAY, null, false);
        acceptType("Corporate Accommodation", SpaceType.CORPORATE_STAY, null, false);
        acceptType("Corporate Housing", SpaceType.CORPORATE_STAY, null, false);
        reviewTypes("Corporate Apartment", SpaceType.CORPORATE_STAY, SpaceType.RENTAL);
        reviewTypes("Corporate Serviced Apartment", SpaceType.CORPORATE_STAY, SpaceType.RENTAL);
        reviewTypes("Company Guest House", SpaceType.CORPORATE_STAY, SpaceType.RENTAL);
        reviewTypes("Mess & PG", SpaceType.MESS, SpaceType.PG);
        reviewTypes("PG / Rental / Co-living", SpaceType.PG, SpaceType.RENTAL, SpaceType.CO_LIVING);
    }

    @Test
    void genders_matchCurrentNormalizer() {
        acceptGender("Gents", GenderPolicy.MALE, null);
        acceptGender("Ladies", GenderPolicy.FEMALE, null);
        acceptGender("Both", GenderPolicy.MIXED, null);
        acceptGender("Both (listing category mixed)", GenderPolicy.MIXED, "read as Both");
        reviewGender("Ladies / Unisex");
        acceptGender("Unisex", GenderPolicy.MIXED, null);
        acceptGender("Boys", GenderPolicy.MALE, null);
        acceptGender("Boys & Girls", GenderPolicy.MIXED, null);
        acceptGender("Gents / Ladies", GenderPolicy.MIXED, null);
        reviewGender("Ladies / Both*");
        acceptGender("Girls / Boys*", GenderPolicy.MIXED, null);
        blankGender("-");
        blankGender("");
        assertThat(GenderNormalizer.normalize("Single / Double").error()).contains("sharing");
        assertThat(GenderNormalizer.normalize("Single / Double / Triple").error()).contains("sharing");
    }

    @Test
    void sharing_isFreeTextExceptPlaceholders() {
        for (String value : new String[] {
            "Single",
            "Double",
            "Twin",
            "Triple",
            "Four",
            "Shared",
            "Private",
            "1 BHK",
            "2 BHK",
            "Single / Double",
            "Double / Triple",
            "Double/triple sharing",
            "Single or double sharing",
            "Single / double",
            "Single / Twin",
            "Single / Twin / Triple",
            "Single/Twin/Triple",
            "Twin / Triple",
            "Double / Private",
            "Private / Double / Triple",
            "2 / 3 Sharing",
            "2 & 3 Sharing",
            "Twin/Triple",
            "₹17,999/month",
            "₹18,000/month",
            "₹23,000/month"
        }) {
            assertThat(PropertyBulkImportValueParser.normalizeFreeText(value)).isEqualTo(value);
        }
        assertThat(PropertyBulkImportValueParser.normalizeFreeText("-")).isNull();
        assertThat(PropertyBulkImportValueParser.normalizeFreeText("")).isNull();
        assertThat(PropertyBulkImportValueParser.normalizeFreeText(null)).isNull();
    }

    @Test
    void rents_keepASingleNumberAndDropRecognizedUnits() {
        acceptRent("₹7,000", "7000");
        acceptRent("₹2,500", "2500");
        acceptRent("₹3,000*", "3000");
        acceptRent("₹15,000", "15000");
        acceptRent("₹12,000", "12000");
        acceptRent("₹5,500", "5500");
        acceptRent("₹11,000", "11000");
        acceptRent("₹16,500", "16500");
        acceptRent("₹10,000", "10000");
        acceptRent("₹12,500", "12500");
        acceptRent("₹8,500", "8500");
        acceptRent("₹22,000", "22000");
        acceptRent("₹9,135", "9135");
        acceptRent("₹7,500", "7500");
        acceptRent("₹18,000", "18000");
        acceptRent("₹24,000", "24000");
        acceptRent("₹6,999", "6999");
        acceptRent("₹3,500", "3500");
        acceptRent("6000", "6000");
        acceptRent("4500", "4500");
        acceptRent("₹8,000/bed", "8000");
        assertThat(PropertyBulkImportValueParser.parseStartingPrice("₹8,000/bed").basis())
                .isEqualTo(com.acomi.acomi_backend.property.domain.model.PriceBasis.PER_BED);
        acceptRent("₹17,999/month", "17999");
        acceptRent("₹18,000/month", "18000");
        acceptRent("₹23,000/month", "23000");
        acceptRent("₹12,000/night", "12000");
        acceptRent("₹1/month", "1");
        acceptRent("₹5,500/month", "5500");
        blankRent("-");
        blankRent("");
        holdRent("₹200 for two (approx.)");
        holdRent("₹150 per order (approx.)");
        holdRent("₹2,800/night listed");
        holdRent("₹250 for two (approx.)");
        holdRent("₹2,700/night listed");
        holdRent("₹100 per order listed");
        rejectRent("Fully furnished");
        holdRent("æ7,000");
        acceptRent("¤7,000", "7000");
    }

    @Test
    void food_isBooleanAndRejectsMostDescriptions() {
        acceptFood("Yes", true, null);
        acceptFood("No", false, null);
        acceptFood("Included", true, null);
        reviewFood("Available");
        reviewFood("Extra charge");
        reviewFood("Optional / separate plan");
        reviewFood("Food charge extra");
        reviewFood("No forced mess fee");
        reviewFood("Yes (meal service)");
        reviewFood("Yes, meal service");
        reviewFood("Mess available");
        reviewFood("Yes, 3 meals/day");
        reviewFood("Yes, three meals reported");
        reviewFood("Yes, tiffin service");
        reviewFood("Yes, 2 meals/day");
        reviewFood("No; tiffin extra");
        reviewFood("Meals listed");
        reviewFood("Breakfast available");
        reviewFood("Breakfast included");
        reviewFood("Available separately");
        reviewFood("No / private kitchen");
        reviewFood("Yes — breakfast, lunch & dinner");
        reviewFood("₹2,500/month");
        reviewFood("Restaurant / room service");
        reviewFood("Available / kitchen");
        reviewFood("Room service");
        reviewFood("Complimentary breakfast");
        assertThat(FoodIncludedNormalizer.normalize("-").value()).isNull();
        assertThat(FoodIncludedNormalizer.normalize("").value()).isNull();
        assertThat(FoodIncludedNormalizer.normalize("-").error()).isNull();
    }

    @Test
    void contactsCoordinatesAndLinks_followCurrentRules() {
        var mobile = PropertyBulkImportValueParser.parseContactPhone("+91 98765 43210");
        assertThat(mobile.value()).isEqualTo("9876543210");
        assertThat(mobile.kind()).isEqualTo(PropertyBulkImportValueParser.PhoneKind.MOBILE);

        var trunk = PropertyBulkImportValueParser.parseContactPhone("09876543210");
        assertThat(trunk.value()).isEqualTo("9876543210");
        assertThat(trunk.kind()).isEqualTo(PropertyBulkImportValueParser.PhoneKind.MOBILE);

        var landline = PropertyBulkImportValueParser.parseContactPhone("040-12345678");
        assertThat(landline.kind()).isEqualTo(PropertyBulkImportValueParser.PhoneKind.LANDLINE);

        assertThat(PropertyBulkImportValueParser.parseContactPhone("12345").isInvalid()).isTrue();
        assertThat(PropertyBulkImportValueParser.parseContactPhone("ABC").isInvalid()).isTrue();
        assertThat(PropertyBulkImportValueParser.parseContactPhone("-").isBlank()).isTrue();

        assertThat(PropertyBulkImportValueParser.parseCoordinate("17.3850", "Latitude").value())
                .isEqualByComparingTo("17.3850");
        assertThat(PropertyBulkImportValueParser.parseCoordinate("91", "Latitude").isInvalid()).isTrue();
        assertThat(PropertyBulkImportValueParser.parseCoordinate("181", "Longitude").isInvalid()).isTrue();
        assertThat(PropertyBulkImportValueParser.parseCoordinate("-", "Latitude").value()).isNull();
        assertThat(PropertyBulkImportValueParser.parseCoordinate("17.3850, 78.4867", "Latitude").isInvalid())
                .isTrue();

        assertThat(PropertyBulkImportValueParser.blankToNull(
                        "https://maps.google.com/?q=17.385,78.486"))
                .startsWith("https://");
        assertThat(PropertyBulkImportValueParser.blankToNull("https://maps.app.goo.gl/abc")).isNotNull();
        assertThat(PropertyBulkImportValueParser.blankToNull("Google Maps")).isEqualTo("Google Maps");
        assertThat(ListingMapLocation.looksLikeHttpUrl("Google Maps")).isFalse();
        assertThat(ListingMapLocation.looksLikeHttpUrl("https://maps.app.goo.gl/abc")).isTrue();
        assertThat(PropertyBulkImportValueParser.blankToNull("-")).isNull();
    }

    @Test
    void amenities_splitKnownCodesAndKeepUnknownText() {
        var parsed = PropertyBulkImportValueParser.parseAmenities("WiFi, Washing Machine; Custom lounge | CCTV");
        assertThat(parsed.amenities()).extracting(a -> a.getCode()).containsExactly("WIFI", "WASHING_MACHINE", "CCTV");
        assertThat(parsed.unmappedAmenities()).isEqualTo("Custom lounge");
        assertThat(PropertyBulkImportValueParser.parseAmenities("-").amenities()).isEmpty();
    }

    private static void acceptType(String raw, SpaceType type, String warningPart, boolean warning) {
        SpaceTypeNormalizer.Result result = SpaceTypeNormalizer.normalize(raw);
        assertThat(result.error()).isNull();
        assertThat(result.type()).isEqualTo(type);
        if (warning) {
            assertThat(result.warning()).contains(warningPart);
        } else {
            assertThat(result.warning()).isNull();
        }
    }

    private static void rejectType(String raw) {
        assertThat(SpaceTypeNormalizer.normalize(raw).error()).contains("Unknown property type");
    }

    private static void rejectMixed(String raw) {
        SpaceTypeNormalizer.Result result = SpaceTypeNormalizer.normalize(raw);
        assertThat(result.type()).isNull();
        assertThat(result.error()).contains("Choose one primary type");
        assertThat(result.candidates()).isNotEmpty();
    }

    private static void reviewTypes(String raw, SpaceType... expected) {
        SpaceTypeNormalizer.Result result = SpaceTypeNormalizer.normalize(raw);
        assertThat(result.type()).isNull();
        assertThat(result.error()).contains("Only that type is saved");
        assertThat(result.candidates()).containsExactlyInAnyOrder(expected);
    }

    private static void blankType(String raw) {
        SpaceTypeNormalizer.Result result = SpaceTypeNormalizer.normalize(raw);
        assertThat(result.type()).isNull();
        assertThat(result.error()).isNull();
    }

    private static void acceptGender(String raw, GenderPolicy policy, String warningPart) {
        GenderNormalizer.Result result = GenderNormalizer.normalize(raw);
        assertThat(result.error()).isNull();
        assertThat(result.value()).isEqualTo(policy);
        if (warningPart == null) {
            assertThat(result.warning()).isNull();
        } else {
            assertThat(result.warning()).contains(warningPart);
        }
    }

    private static void rejectGender(String raw) {
        assertThat(GenderNormalizer.normalize(raw).error()).contains("Unknown gender policy");
    }

    private static void reviewGender(String raw) {
        GenderNormalizer.Result result = GenderNormalizer.normalize(raw);
        assertThat(result.value()).isNull();
        assertThat(result.error()).isNull();
        assertThat(result.warning()).contains("was not changed");
    }

    private static void blankGender(String raw) {
        GenderNormalizer.Result result = GenderNormalizer.normalize(raw);
        assertThat(result.value()).isNull();
        assertThat(result.error()).isNull();
    }

    private static void acceptRent(String raw, String number) {
        var result = PropertyBulkImportValueParser.parseStartingPrice(raw);
        assertThat(result.error()).as(raw).isNull();
        assertThat(result.value()).isEqualByComparingTo(new BigDecimal(number));
    }

    private static void rejectRent(String raw) {
        assertThat(PropertyBulkImportValueParser.parseStartingPrice(raw).isInvalid()).as(raw).isTrue();
    }

    private static void holdRent(String raw) {
        var result = PropertyBulkImportValueParser.parseStartingPrice(raw);
        assertThat(result.value()).as(raw).isNull();
        assertThat(result.error()).as(raw).isNull();
        assertThat(result.warning()).as(raw).isNotBlank();
    }

    private static void blankRent(String raw) {
        var result = PropertyBulkImportValueParser.parseStartingPrice(raw);
        assertThat(result.value()).isNull();
        assertThat(result.error()).isNull();
    }

    private static void acceptFood(String raw, boolean value, String warningPart) {
        FoodIncludedNormalizer.Result result = FoodIncludedNormalizer.normalize(raw);
        assertThat(result.error()).isNull();
        assertThat(result.value()).isEqualTo(value);
        if (warningPart == null) {
            assertThat(result.warning()).isNull();
        } else {
            assertThat(result.warning()).containsIgnoringCase(warningPart);
        }
    }

    private static void reviewFood(String raw) {
        FoodIncludedNormalizer.Result result = FoodIncludedNormalizer.normalize(raw);
        assertThat(result.value()).as(raw).isNull();
        assertThat(result.error()).as(raw).isNull();
        assertThat(result.warning()).as(raw).isNotBlank();
    }
}
