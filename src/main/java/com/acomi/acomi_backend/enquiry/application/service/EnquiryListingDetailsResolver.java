package com.acomi.acomi_backend.enquiry.application.service;

import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.domain.model.PriceBasis;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationAmenityEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.registration.application.AdminLeadDefaults;
import com.acomi.acomi_backend.space.api.dto.AmenityAssignmentDto;
import com.acomi.acomi_backend.space.application.service.DiscoverListingSanitizer;
import com.acomi.acomi_backend.space.application.service.SpaceAmenityService;
import com.acomi.acomi_backend.space.domain.model.AmenityCode;
import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds customer-facing listing details for authorised share emails from the converted
 * registration and Space. Never includes admin identity, review notes, or claim metadata.
 */
@Component
@RequiredArgsConstructor
public class EnquiryListingDetailsResolver {

    private final PropertyRegistrationRepository propertyRegistrationRepository;
    private final MessRegistrationRepository messRegistrationRepository;
    private final SpaceAmenityService spaceAmenityService;

    @Transactional(readOnly = true)
    public EnquiryListingDetails resolve(SpaceEntity space) {
        if (space == null) {
            return EnquiryListingDetails.empty();
        }
        List<AmenityAssignmentDto> spaceAmenities =
                space.getId() == null ? List.of() : spaceAmenityService.getForSpace(space.getId());
        if (space.getType() == SpaceType.MESS) {
            MessRegistrationEntity mess =
                    messRegistrationRepository.findByConvertedSpaceId(space.getId()).orElse(null);
            if (mess != null) {
                return fromMess(space, mess, spaceAmenities);
            }
        } else {
            PropertyRegistrationEntity property =
                    propertyRegistrationRepository.findByConvertedSpaceId(space.getId()).orElse(null);
            if (property != null) {
                return fromProperty(space, property, spaceAmenities);
            }
        }
        return fromSpace(space, spaceAmenities, null);
    }

    private static EnquiryListingDetails fromProperty(
            SpaceEntity space, PropertyRegistrationEntity property, List<AmenityAssignmentDto> spaceAmenities) {
        String addressLine = DiscoverListingSanitizer.text(property.getAddressLine());
        String city = DiscoverListingSanitizer.text(property.getCity());
        String state = DiscoverListingSanitizer.text(property.getState());
        String pincode = pincode(property.getPincode(), addressLine, city, state);
        BigDecimal latitude =
                DiscoverListingSanitizer.firstLatitude(space.getLatitude(), property.getLatitude());
        BigDecimal longitude =
                DiscoverListingSanitizer.firstLongitude(space.getLongitude(), property.getLongitude());
        return new EnquiryListingDetails(
                DiscoverListingSanitizer.text(property.getDescription()),
                addressLine,
                city,
                state,
                pincode,
                location(addressLine, city, state, pincode, space.getAddress()),
                mapsLink(property.getMapUrl(), latitude, longitude),
                coordinates(latitude, longitude),
                gender(firstGender(property.getGenderPolicy(), space.getGenderPolicy())),
                food(property.getFoodIncludedListing()),
                money(property.getStartingPrice()),
                basis(property.getPriceBasis()),
                null,
                null,
                capacity(property.getCapacityEstimate()),
                DiscoverListingSanitizer.text(property.getSharingNotes()),
                joinAmenities(spaceAmenities, registrationAmenityLabels(property), property.getUnmappedAmenities()));
    }

    private static EnquiryListingDetails fromMess(
            SpaceEntity space, MessRegistrationEntity mess, List<AmenityAssignmentDto> spaceAmenities) {
        String addressLine = DiscoverListingSanitizer.text(mess.getAddressLine());
        String city = DiscoverListingSanitizer.text(mess.getCity());
        String state = DiscoverListingSanitizer.text(mess.getState());
        String pincode = pincode(mess.getPincode(), addressLine, city, state);
        BigDecimal latitude = DiscoverListingSanitizer.firstLatitude(space.getLatitude(), mess.getLatitude());
        BigDecimal longitude = DiscoverListingSanitizer.firstLongitude(space.getLongitude(), mess.getLongitude());
        return new EnquiryListingDetails(
                DiscoverListingSanitizer.text(mess.getDescription()),
                addressLine,
                city,
                state,
                pincode,
                location(addressLine, city, state, pincode, space.getAddress()),
                mapsLink(mess.getMapUrl(), latitude, longitude),
                coordinates(latitude, longitude),
                gender(firstGender(mess.getGenderPolicy(), space.getGenderPolicy())),
                food(mess.getFoodIncludedListing()),
                null,
                null,
                money(mess.getMonthlyPrice()),
                money(mess.getMealPrice()),
                capacity(mess.getCapacityEstimate()),
                DiscoverListingSanitizer.text(mess.getSharingNotes()),
                joinAmenities(spaceAmenities, List.of(), mess.getUnmappedAmenities()));
    }

    private static EnquiryListingDetails fromSpace(
            SpaceEntity space, List<AmenityAssignmentDto> spaceAmenities, String unmappedAmenities) {
        BigDecimal latitude = DiscoverListingSanitizer.latitude(space.getLatitude());
        BigDecimal longitude = DiscoverListingSanitizer.longitude(space.getLongitude());
        String location = usableSpaceAddress(space.getAddress());
        return new EnquiryListingDetails(
                null,
                null,
                null,
                null,
                null,
                location,
                mapsLink(null, latitude, longitude),
                coordinates(latitude, longitude),
                gender(space.getGenderPolicy()),
                food(space.isFoodIncludedInRent()),
                null,
                null,
                null,
                null,
                null,
                null,
                joinAmenities(spaceAmenities, List.of(), unmappedAmenities));
    }

    private static String pincode(String raw, String addressLine, String city, String state) {
        String pin = DiscoverListingSanitizer.text(raw);
        if (pin == null) {
            return null;
        }
        boolean addressMissing = addressLine == null && city == null && state == null;
        if (addressMissing && AdminLeadDefaults.PLACEHOLDER_PINCODE.equals(pin)) {
            return null;
        }
        return pin;
    }

    private static String location(
            String addressLine, String city, String state, String pincode, String spaceAddress) {
        List<String> parts = new ArrayList<>();
        addPart(parts, addressLine);
        addPart(parts, city);
        addPart(parts, state);
        addPart(parts, pincode);
        if (!parts.isEmpty()) {
            return String.join(", ", parts);
        }
        return usableSpaceAddress(spaceAddress);
    }

    private static String usableSpaceAddress(String spaceAddress) {
        String text = DiscoverListingSanitizer.text(spaceAddress);
        if (text == null || AdminLeadDefaults.PLACEHOLDER_PINCODE.equals(text)) {
            return null;
        }
        return text;
    }

    private static String mapsLink(String mapUrl, BigDecimal latitude, BigDecimal longitude) {
        String sanitized = DiscoverListingSanitizer.mapUrl(mapUrl);
        if (sanitized != null) {
            return sanitized;
        }
        if (latitude == null || longitude == null) {
            return null;
        }
        return "https://maps.google.com/?q="
                + latitude.stripTrailingZeros().toPlainString()
                + ","
                + longitude.stripTrailingZeros().toPlainString();
    }

    private static String coordinates(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }
        return latitude.stripTrailingZeros().toPlainString()
                + ", "
                + longitude.stripTrailingZeros().toPlainString();
    }

    private static GenderPolicy firstGender(GenderPolicy preferred, GenderPolicy fallback) {
        return preferred != null ? preferred : fallback;
    }

    private static String gender(GenderPolicy policy) {
        if (policy == null) {
            return null;
        }
        return switch (policy) {
            case MALE -> "Male";
            case FEMALE -> "Female";
            case MIXED -> "Mixed";
        };
    }

    private static String food(Boolean value) {
        if (value == null) {
            return null;
        }
        return value ? "Yes" : "No";
    }

    private static String money(BigDecimal value) {
        BigDecimal sanitized = DiscoverListingSanitizer.positivePrice(value);
        if (sanitized == null) {
            return null;
        }
        return "INR " + sanitized.stripTrailingZeros().toPlainString();
    }

    private static String basis(PriceBasis priceBasis) {
        if (priceBasis == null) {
            return null;
        }
        return switch (priceBasis) {
            case PER_BED -> "Per bed";
            case PER_ROOM -> "Per room";
            case PER_UNIT -> "Per unit";
        };
    }

    private static String capacity(Integer value) {
        if (value == null || value <= 0) {
            return null;
        }
        return String.valueOf(value);
    }

    private static List<String> registrationAmenityLabels(PropertyRegistrationEntity property) {
        if (property.getAmenities() == null || property.getAmenities().isEmpty()) {
            return List.of();
        }
        List<String> labels = new ArrayList<>();
        for (PropertyRegistrationAmenityEntity amenity : property.getAmenities()) {
            addPart(labels, amenityLabel(amenity));
        }
        return labels;
    }

    private static String amenityLabel(PropertyRegistrationAmenityEntity amenity) {
        String custom = DiscoverListingSanitizer.text(amenity.getCustomLabel());
        if (custom != null) {
            return custom;
        }
        AmenityCode code = AmenityCode.fromValue(amenity.getAmenityCode()).orElse(AmenityCode.CUSTOM);
        if (code.getDefaultLabel() != null) {
            return code.getDefaultLabel();
        }
        return DiscoverListingSanitizer.text(amenity.getAmenityCode());
    }

    private static String joinAmenities(
            List<AmenityAssignmentDto> spaceAmenities, List<String> registrationLabels, String unmapped) {
        List<String> labels = new ArrayList<>();
        if (spaceAmenities != null) {
            for (AmenityAssignmentDto amenity : spaceAmenities) {
                addPart(labels, amenity != null ? DiscoverListingSanitizer.text(amenity.getLabel()) : null);
            }
        }
        if (labels.isEmpty() && registrationLabels != null) {
            for (String label : registrationLabels) {
                addPart(labels, label);
            }
        }
        addPart(labels, DiscoverListingSanitizer.text(unmapped));
        return labels.isEmpty() ? null : String.join(", ", labels);
    }

    private static void addPart(List<String> parts, String value) {
        if (value != null && !parts.contains(value)) {
            parts.add(value);
        }
    }
}
