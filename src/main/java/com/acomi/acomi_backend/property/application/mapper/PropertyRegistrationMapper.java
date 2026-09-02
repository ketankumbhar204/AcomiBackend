package com.acomi.acomi_backend.property.application.mapper;

import com.acomi.acomi_backend.property.api.dto.response.PropertyRegistrationDetailResponse;
import com.acomi.acomi_backend.property.api.dto.response.PropertyRegistrationListItemResponse;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationAmenityEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import java.util.List;

public final class PropertyRegistrationMapper {

    private PropertyRegistrationMapper() {}

    public static PropertyRegistrationListItemResponse toListItem(PropertyRegistrationEntity entity) {
        return PropertyRegistrationListItemResponse.builder()
                .id(entity.getId())
                .reference(entity.getReference())
                .propertyType(entity.getPropertyType())
                .propertyName(entity.getPropertyName())
                .ownerName(entity.getOwnerName())
                .mobileNumber(entity.getMobileNumber())
                .alternateMobileNumber(entity.getAlternateMobileNumber())
                .city(entity.getCity())
                .state(entity.getState())
                .pincode(entity.getPincode())
                .status(entity.getStatus())
                .source(entity.getSource())
                .claimedAt(entity.getClaimedAt())
                .createdAt(entity.getCreatedAt())
                .testLead(entity.isTestLead())
                .build();
    }

    public static PropertyRegistrationDetailResponse toDetail(PropertyRegistrationEntity entity) {
        return PropertyRegistrationDetailResponse.builder()
                .id(entity.getId())
                .reference(entity.getReference())
                .propertyType(entity.getPropertyType())
                .propertyName(entity.getPropertyName())
                .ownerName(entity.getOwnerName())
                .mobileNumber(entity.getMobileNumber())
                .alternateMobileNumber(entity.getAlternateMobileNumber())
                .mobileVerifiedAt(entity.getMobileVerifiedAt())
                .description(entity.getDescription())
                .addressLine(entity.getAddressLine())
                .city(entity.getCity())
                .state(entity.getState())
                .pincode(entity.getPincode())
                .mapUrl(entity.getMapUrl())
                .startingPrice(entity.getStartingPrice())
                .priceBasis(entity.getPriceBasis())
                .capacityEstimate(entity.getCapacityEstimate())
                .status(entity.getStatus())
                .source(entity.getSource())
                .convertedSpaceId(entity.getConvertedSpaceId())
                .claimedAt(entity.getClaimedAt())
                .claimedVia(entity.getClaimedVia())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .testLead(entity.isTestLead())
                .amenities(toAmenities(entity.getAmenities()))
                .build();
    }

    private static List<PropertyRegistrationDetailResponse.PropertyRegistrationAmenityResponse> toAmenities(
            List<PropertyRegistrationAmenityEntity> amenities) {
        return amenities.stream()
                .map(amenity -> PropertyRegistrationDetailResponse.PropertyRegistrationAmenityResponse.builder()
                        .code(amenity.getAmenityCode())
                        .customLabel(amenity.getCustomLabel())
                        .displayOrder(amenity.getDisplayOrder())
                        .build())
                .toList();
    }
}
