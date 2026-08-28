package com.acomi.acomi_backend.registration.application;

import com.acomi.acomi_backend.mess.application.service.MessRegistrationPayload;
import com.acomi.acomi_backend.property.application.service.PropertyRegistrationPayload;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.math.BigDecimal;
import org.springframework.util.StringUtils;

/** Placeholder values for optional admin lead fields (DB columns are NOT NULL). */
public final class AdminLeadDefaults {

    private static final String UNTITLED_PROPERTY = "Untitled property";
    private static final String UNTITLED_MESS = "Untitled mess";
    private static final String UNKNOWN_OWNER = "Unknown";
    private static final String PLACEHOLDER_MOBILE = "6000000000";
    private static final String PLACEHOLDER_ADDRESS = "—";
    private static final String PLACEHOLDER_PINCODE = "110001";

    private AdminLeadDefaults() {}

    public static PropertyRegistrationPayload normalizeProperty(PropertyRegistrationPayload payload) {
        SpaceType propertyType = payload.getPropertyType() != null ? payload.getPropertyType() : SpaceType.PG;
        return PropertyRegistrationPayload.builder()
                .propertyType(propertyType)
                .propertyName(textOr(payload.getPropertyName(), UNTITLED_PROPERTY))
                .ownerName(textOr(payload.getOwnerName(), UNKNOWN_OWNER))
                .description(trimOrNull(payload.getDescription()))
                .mobileNumber(mobileOr(payload.getMobileNumber()))
                .addressLine(textOr(payload.getAddressLine(), PLACEHOLDER_ADDRESS))
                .city(textOr(payload.getCity(), PLACEHOLDER_ADDRESS))
                .state(textOr(payload.getState(), PLACEHOLDER_ADDRESS))
                .pincode(pincodeOr(payload.getPincode()))
                .mapUrl(trimOrNull(payload.getMapUrl()))
                .startingPrice(payload.getStartingPrice() != null ? payload.getStartingPrice() : BigDecimal.ZERO)
                .capacityEstimate(payload.getCapacityEstimate())
                .amenities(payload.getAmenities())
                .testLead(payload.getTestLead())
                .build();
    }

    public static MessRegistrationPayload normalizeMess(MessRegistrationPayload payload) {
        return MessRegistrationPayload.builder()
                .messName(textOr(payload.getMessName(), UNTITLED_MESS))
                .ownerName(textOr(payload.getOwnerName(), UNKNOWN_OWNER))
                .description(trimOrNull(payload.getDescription()))
                .mobileNumber(mobileOr(payload.getMobileNumber()))
                .addressLine(textOr(payload.getAddressLine(), PLACEHOLDER_ADDRESS))
                .city(textOr(payload.getCity(), PLACEHOLDER_ADDRESS))
                .state(textOr(payload.getState(), PLACEHOLDER_ADDRESS))
                .pincode(pincodeOr(payload.getPincode()))
                .mapUrl(trimOrNull(payload.getMapUrl()))
                .monthlyPrice(payload.getMonthlyPrice() != null ? payload.getMonthlyPrice() : BigDecimal.ZERO)
                .mealPrice(payload.getMealPrice() != null ? payload.getMealPrice() : BigDecimal.ZERO)
                .capacityEstimate(payload.getCapacityEstimate())
                .testLead(payload.getTestLead())
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
