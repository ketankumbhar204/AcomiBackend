package com.acomi.acomi_backend.registration.application;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.mess.application.service.MessRegistrationPayload;
import com.acomi.acomi_backend.property.application.service.PropertyRegistrationPayload;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

/** Placeholder values for optional admin lead fields (DB columns are NOT NULL). */
public final class AdminLeadDefaults {

    private static final String UNTITLED_PROPERTY = "Untitled property";
    private static final String UNTITLED_MESS = "Untitled mess";
    public static final String UNKNOWN_OWNER = "Unknown";
    public static final String PLACEHOLDER_MOBILE = "6000000000";
    public static final String PLACEHOLDER_ADDRESS = "—";
    public static final String PLACEHOLDER_PINCODE = "110001";

    private AdminLeadDefaults() {}

    public static PropertyRegistrationPayload normalizeProperty(PropertyRegistrationPayload payload) {
        SpaceType propertyType = payload.getPropertyType() != null ? payload.getPropertyType() : SpaceType.PG;
        return PropertyRegistrationPayload.builder()
                .propertyType(propertyType)
                .propertyName(textOr(payload.getPropertyName(), UNTITLED_PROPERTY))
                .ownerName(textOr(payload.getOwnerName(), UNKNOWN_OWNER))
                .description(trimOrNull(payload.getDescription()))
                .mobileNumber(mobileOr(payload.getMobileNumber()))
                .alternateMobileNumber(trimOrNull(payload.getAlternateMobileNumber()))
                .additionalMobileNumber(trimOrNull(payload.getAdditionalMobileNumber()))
                .addressLine(textOr(payload.getAddressLine(), PLACEHOLDER_ADDRESS))
                .city(textOr(payload.getCity(), PLACEHOLDER_ADDRESS))
                .state(textOr(payload.getState(), PLACEHOLDER_ADDRESS))
                .pincode(pincodeOr(payload.getPincode()))
                .mapUrl(trimOrNull(payload.getMapUrl()))
                .latitude(payload.getLatitude())
                .longitude(payload.getLongitude())
                .startingPrice(payload.getStartingPrice() != null ? payload.getStartingPrice() : BigDecimal.ZERO)
                .priceBasis(payload.getPriceBasis())
                .capacityEstimate(payload.getCapacityEstimate())
                .amenities(payload.getAmenities())
                .testLead(payload.getTestLead())
                .sharingNotes(trimOrNull(payload.getSharingNotes()))
                .foodIncludedListing(payload.getFoodIncludedListing())
                .genderPolicy(payload.getGenderPolicy())
                .unmappedAmenities(trimOrNull(payload.getUnmappedAmenities()))
                .build();
    }

    public static MessRegistrationPayload normalizeMess(MessRegistrationPayload payload) {
        return MessRegistrationPayload.builder()
                .messName(textOr(payload.getMessName(), UNTITLED_MESS))
                .ownerName(textOr(payload.getOwnerName(), UNKNOWN_OWNER))
                .description(trimOrNull(payload.getDescription()))
                .mobileNumber(mobileOr(payload.getMobileNumber()))
                .alternateMobileNumber(trimOrNull(payload.getAlternateMobileNumber()))
                .additionalMobileNumber(trimOrNull(payload.getAdditionalMobileNumber()))
                .addressLine(textOr(payload.getAddressLine(), PLACEHOLDER_ADDRESS))
                .city(textOr(payload.getCity(), PLACEHOLDER_ADDRESS))
                .state(textOr(payload.getState(), PLACEHOLDER_ADDRESS))
                .pincode(pincodeOr(payload.getPincode()))
                .mapUrl(trimOrNull(payload.getMapUrl()))
                .latitude(payload.getLatitude())
                .longitude(payload.getLongitude())
                .monthlyPrice(payload.getMonthlyPrice() != null ? payload.getMonthlyPrice() : BigDecimal.ZERO)
                .monthlyPriceBasis(payload.getMonthlyPriceBasis())
                .mealPrice(payload.getMealPrice() != null ? payload.getMealPrice() : BigDecimal.ZERO)
                .capacityEstimate(payload.getCapacityEstimate())
                .testLead(payload.getTestLead())
                .sharingNotes(trimOrNull(payload.getSharingNotes()))
                .foodIncludedListing(payload.getFoodIncludedListing())
                .genderPolicy(payload.getGenderPolicy())
                .unmappedAmenities(trimOrNull(payload.getUnmappedAmenities()))
                .build();
    }

    /**
     * Import-only. Space type and property name are required. Every other Excel value stays empty
     * when the cell is blank. Owner is not an Excel column, so a blank owner stays "Unknown".
     */
    public static PropertyRegistrationPayload normalizeImportedProperty(PropertyRegistrationPayload payload) {
        List<String> missing = new ArrayList<>();
        if (payload.getPropertyType() == null) {
            missing.add("Space type");
        }
        if (!StringUtils.hasText(payload.getPropertyName())) {
            missing.add("Property name");
        }
        if (!missing.isEmpty()) {
            throw new BusinessException(
                    "This row was not saved. Missing " + String.join(", ", missing)
                            + ". Nothing was invented for those cells.",
                    HttpStatus.BAD_REQUEST);
        }
        return PropertyRegistrationPayload.builder()
                .propertyType(payload.getPropertyType())
                .propertyName(payload.getPropertyName().trim())
                .ownerName(textOr(payload.getOwnerName(), UNKNOWN_OWNER))
                .description(trimOrNull(payload.getDescription()))
                .mobileNumber(trimOrNull(payload.getMobileNumber()))
                .alternateMobileNumber(trimOrNull(payload.getAlternateMobileNumber()))
                .additionalMobileNumber(trimOrNull(payload.getAdditionalMobileNumber()))
                .addressLine(trimOrNull(payload.getAddressLine()))
                .city(trimOrNull(payload.getCity()))
                .state(trimOrNull(payload.getState()))
                .pincode(trimOrNull(payload.getPincode()))
                .mapUrl(trimOrNull(payload.getMapUrl()))
                .latitude(payload.getLatitude())
                .longitude(payload.getLongitude())
                .startingPrice(payload.getStartingPrice())
                .priceBasis(payload.getPriceBasis())
                .capacityEstimate(payload.getCapacityEstimate())
                .amenities(payload.getAmenities())
                .testLead(payload.getTestLead())
                .sharingNotes(trimOrNull(payload.getSharingNotes()))
                .foodIncludedListing(payload.getFoodIncludedListing())
                .genderPolicy(payload.getGenderPolicy())
                .unmappedAmenities(trimOrNull(payload.getUnmappedAmenities()))
                .build();
    }

    /**
     * Import-only. Mess name is required. Contact, address, and rent stay empty when blank.
     * Meal price is not an Excel column, so a missing meal price stays zero.
     */
    public static MessRegistrationPayload normalizeImportedMess(MessRegistrationPayload payload) {
        if (!StringUtils.hasText(payload.getMessName())) {
            throw new BusinessException(
                    "This row was not saved. Missing Property name. Nothing was invented for those cells.",
                    HttpStatus.BAD_REQUEST);
        }
        return MessRegistrationPayload.builder()
                .messName(payload.getMessName().trim())
                .ownerName(textOr(payload.getOwnerName(), UNKNOWN_OWNER))
                .description(trimOrNull(payload.getDescription()))
                .mobileNumber(trimOrNull(payload.getMobileNumber()))
                .alternateMobileNumber(trimOrNull(payload.getAlternateMobileNumber()))
                .additionalMobileNumber(trimOrNull(payload.getAdditionalMobileNumber()))
                .addressLine(trimOrNull(payload.getAddressLine()))
                .city(trimOrNull(payload.getCity()))
                .state(trimOrNull(payload.getState()))
                .pincode(trimOrNull(payload.getPincode()))
                .mapUrl(trimOrNull(payload.getMapUrl()))
                .latitude(payload.getLatitude())
                .longitude(payload.getLongitude())
                .monthlyPrice(payload.getMonthlyPrice())
                .monthlyPriceBasis(payload.getMonthlyPriceBasis())
                .mealPrice(payload.getMealPrice() != null ? payload.getMealPrice() : BigDecimal.ZERO)
                .capacityEstimate(payload.getCapacityEstimate())
                .testLead(payload.getTestLead())
                .sharingNotes(trimOrNull(payload.getSharingNotes()))
                .foodIncludedListing(payload.getFoodIncludedListing())
                .genderPolicy(payload.getGenderPolicy())
                .unmappedAmenities(trimOrNull(payload.getUnmappedAmenities()))
                .build();
    }

    private static String textOr(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static String trimOrNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String mobileOr(String value) {
        return StringUtils.hasText(value) ? value.trim() : PLACEHOLDER_MOBILE;
    }

    private static String pincodeOr(String value) {
        return StringUtils.hasText(value) ? value.trim() : PLACEHOLDER_PINCODE;
    }
}
