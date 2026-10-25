package com.acomi.acomi_backend.admin.bulkimport.property;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PropertyBulkImportNormalizerTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @ParameterizedTest
    @CsvSource({
        "PG, PG",
        "P.G., PG",
        "PG Accommodation, PG",
        "Women's PG, PG",
        "Men's PG, PG",
        "Boys Hostel, HOSTEL",
        "Girls Hostel, HOSTEL",
        "Working Women's Hostel, HOSTEL",
        "Co-living, CO_LIVING",
        "Coliving, CO_LIVING",
        "Rental Apartment, RENTAL",
        "Serviced Apartment, RENTAL",
        "Mess, MESS",
        "Tiffin, MESS",
        "Mess / Tiffin, MESS"
    })
    void spaceType_knownAliases(String raw, SpaceType expected) {
        var result = SpaceTypeNormalizer.normalize(raw);
        assertThat(result.error()).isNull();
        assertThat(result.type()).isEqualTo(expected);
    }

    @Test
    void spaceType_womensPg_infersLadiesWithoutWarning() {
        var result = SpaceTypeNormalizer.normalize("Women's PG");
        assertThat(result.type()).isEqualTo(SpaceType.PG);
        assertThat(result.inferredGender()).isEqualTo(GenderPolicy.FEMALE);
        assertThat(result.warning()).isNull();
    }

    @Test
    void spaceType_mensPg_infersGents() {
        assertThat(SpaceTypeNormalizer.normalize("Men's PG").inferredGender()).isEqualTo(GenderPolicy.MALE);
    }

    @Test
    void spaceType_boysHostel_infersGents() {
        var result = SpaceTypeNormalizer.normalize("Boys Hostel");
        assertThat(result.type()).isEqualTo(SpaceType.HOSTEL);
        assertThat(result.inferredGender()).isEqualTo(GenderPolicy.MALE);
    }

    @Test
    void spaceType_girlsHostel_infersLadies() {
        assertThat(SpaceTypeNormalizer.normalize("Girls Hostel").inferredGender()).isEqualTo(GenderPolicy.FEMALE);
    }

    @Test
    void spaceType_workingWomensHostel_infersLadies() {
        var result = SpaceTypeNormalizer.normalize("Working Women's Hostel");
        assertThat(result.type()).isEqualTo(SpaceType.HOSTEL);
        assertThat(result.inferredGender()).isEqualTo(GenderPolicy.FEMALE);
        assertThat(result.warning()).isNull();
    }

    @Test
    void spaceType_pgHostel_needsOnePrimaryType() {
        var result = SpaceTypeNormalizer.normalize("PG / Hostel");
        assertThat(result.type()).isNull();
        assertThat(result.error()).contains("Only that type is saved");
        assertThat(result.candidates()).containsExactlyInAnyOrder(SpaceType.PG, SpaceType.HOSTEL);
    }

    @Test
    void spaceType_messTiffin_hasNoWarning() {
        var result = SpaceTypeNormalizer.normalize("Mess / Tiffin");
        assertThat(result.type()).isEqualTo(SpaceType.MESS);
        assertThat(result.warning()).isNull();
    }

    @Test
    void spaceType_messWithCurryPoint_staysMess() {
        var result = SpaceTypeNormalizer.normalize("Mess / Curry Point");
        assertThat(result.type()).isEqualTo(SpaceType.MESS);
        assertThat(result.error()).isNull();
    }

    @Test
    void spaceType_foodCourt_staysInvalid() {
        var result = SpaceTypeNormalizer.normalize("Food Court / Curry Point");
        assertThat(result.type()).isNull();
        assertThat(result.error()).isEqualTo("Unknown property type: \"Food Court / Curry Point\"");
    }

    @ParameterizedTest
    @CsvSource({
        "Hotel",
        "Restaurant",
        "Office",
        "Shop",
        "Apartment Complex",
        "Accommodation",
        "Property",
        "Room"
    })
    void spaceType_unrelatedLabels_stayInvalid(String raw) {
        assertThat(SpaceTypeNormalizer.normalize(raw).error()).contains("Unknown property type");
    }

    @ParameterizedTest
    @CsvSource({
        "Gents, MALE",
        "Male, MALE",
        "Men, MALE",
        "Boys, MALE",
        "Ladies, FEMALE",
        "Female, FEMALE",
        "Women, FEMALE",
        "Girls, FEMALE",
        "Both, MIXED",
        "Mixed, MIXED",
        "Unisex, MIXED",
        "Any, MIXED",
        "Ladies / Gents, MIXED",
        "Gents / Ladies, MIXED"
    })
    void gender_knownAliases(String raw, GenderPolicy expected) {
        var result = GenderNormalizer.normalize(raw);
        assertThat(result.error()).isNull();
        assertThat(result.warning()).isNull();
        assertThat(result.value()).isEqualTo(expected);
    }

    @Test
    void gender_unknown_isInvalid() {
        var result = GenderNormalizer.normalize("XYZ");
        assertThat(result.value()).isNull();
        assertThat(result.error()).contains("Unknown gender policy");
    }

    @Test
    void gender_girlsPreferred_warns() {
        var result = GenderNormalizer.normalize("Girls preferred");
        assertThat(result.value()).isEqualTo(GenderPolicy.FEMALE);
        assertThat(result.warning()).contains("Please verify");
    }

    @ParameterizedTest
    @CsvSource({
        "Yes, true",
        "Included, true",
        "With food, true",
        "All meals, true",
        "No, false",
        "Not included, false",
        "No food, false"
    })
    void food_clearValues_haveNoWarning(String raw, boolean expected) {
        var result = FoodIncludedNormalizer.normalize(raw);
        assertThat(result.error()).isNull();
        assertThat(result.warning()).isNull();
        assertThat(result.value()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "Food on demand",
        "Optional",
        "Yes/add-on",
        "Food available at extra cost",
        "Meals available",
        "Breakfast",
        "Tiffin available"
    })
    void food_ambiguous_isNoWithWarning(String raw) {
        var result = FoodIncludedNormalizer.normalize(raw);
        assertThat(result.value()).isNull();
        assertThat(result.warning()).isNotBlank();
        assertThat(result.error()).isNull();
    }

    @Test
    void food_unknown_isReviewWarning() {
        var result = FoodIncludedNormalizer.normalize("UnknownRandomValue");
        assertThat(result.value()).isNull();
        assertThat(result.error()).isNull();
        assertThat(result.warning()).contains("not stored as Yes or No");
    }

    @Test
    void row_womensPgBlankGender_infersLadiesAndStaysValid() {
        var processed = process("Women's PG", "", "All meals", "Wi-Fi");
        assertThat(processed.status()).isEqualTo(PropertyBulkImportRowProcessor.RowStatus.VALID);
        assertThat(processed.request().getPropertyType()).isEqualTo(SpaceType.PG);
        assertThat(processed.request().getGenderPolicy()).isEqualTo(GenderPolicy.FEMALE);
        assertThat(processed.request().getFoodIncludedListing()).isTrue();
        assertThat(processed.valuesSnapshot().get("PROPERTY_TYPE")).isEqualTo("Women's PG");
        assertThat(processed.notices()).anyMatch(notice ->
                "NORMALIZED".equals(notice.getLevel()) && notice.getMessage().contains("inferred"));
        assertThat(processed.notices()).noneMatch(notice -> "WARNING".equals(notice.getLevel()));
    }

    @Test
    void row_womensPgWithGents_warnsAndKeepsExplicitGender() {
        var processed = process("Women's PG", "Gents", "Yes", "");
        assertThat(processed.status()).isEqualTo(PropertyBulkImportRowProcessor.RowStatus.WARNING);
        assertThat(processed.request().getGenderPolicy()).isEqualTo(GenderPolicy.MALE);
        assertThat(processed.notices()).anyMatch(notice ->
                notice.getMessage().contains("explicitly") && notice.getMessage().contains("Gents"));
    }

    @Test
    void row_mensHostelWithLadies_warns() {
        var processed = process("Men's Hostel", "Ladies", "No", "");
        assertThat(processed.status()).isEqualTo(PropertyBulkImportRowProcessor.RowStatus.WARNING);
        assertThat(processed.request().getPropertyType()).isEqualTo(SpaceType.HOSTEL);
        assertThat(processed.request().getGenderPolicy()).isEqualTo(GenderPolicy.FEMALE);
    }

    @Test
    void row_foodOnDemand_isWarningAndImportable() {
        var processed = process("Hostel", "Gents", "Food on demand", "");
        assertThat(processed.status()).isEqualTo(PropertyBulkImportRowProcessor.RowStatus.WARNING);
        assertThat(processed.request()).isNotNull();
        assertThat(processed.request().getFoodIncludedListing()).isNull();
    }

    @Test
    void row_foodNoWithMealsIncludedAmenity_warns() {
        var processed = process("PG", "Gents", "No", "Meals included");
        assertThat(processed.status()).isEqualTo(PropertyBulkImportRowProcessor.RowStatus.WARNING);
        assertThat(processed.notices()).anyMatch(notice -> notice.getMessage().contains("Meals included"));
    }

    @Test
    void row_foodCourt_staysInvalid() {
        var processed = process("Food Court / Curry Point", "", "", "");
        assertThat(processed.status()).isEqualTo(PropertyBulkImportRowProcessor.RowStatus.INVALID);
        assertThat(processed.request()).isNull();
        assertThat(processed.errors()).anyMatch(error -> error.getMessage().contains("Food Court / Curry Point"));
    }

    private static PropertyBulkImportRowProcessor.ProcessedRow process(
            String type, String gender, String food, String amenities) {
        Map<String, String> excelRow = new LinkedHashMap<>();
        excelRow.put("Type", type);
        excelRow.put("Name", "Sunrise PG");
        excelRow.put("Mobile", "9876543210");
        excelRow.put("Gender", gender);
        excelRow.put("Food", food);
        excelRow.put("Amenities", amenities);
        Map<String, String> mapping = new LinkedHashMap<>();
        mapping.put("PROPERTY_TYPE", "Type");
        mapping.put("PROPERTY_NAME", "Name");
        mapping.put("MOBILE_NUMBER", "Mobile");
        mapping.put("GENDER", "Gender");
        mapping.put("FOOD_INCLUDED", "Food");
        mapping.put("AMENITIES", "Amenities");
        return PropertyBulkImportRowProcessor.process(excelRow, mapping, validator);
    }
}
