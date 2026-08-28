package com.acomi.acomi_backend.property.application.service;

import com.acomi.acomi_backend.property.api.dto.request.AdminCreatePropertyRegistrationRequest;
import com.acomi.acomi_backend.property.api.dto.request.CreatePropertyRegistrationRequest;
import com.acomi.acomi_backend.space.api.dto.AmenityAssignmentDto;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** Shared property registration payload for public and admin flows. */
@Getter
@Builder
public class PropertyRegistrationPayload {

    private SpaceType propertyType;
    private String propertyName;
    private String ownerName;
    private String description;
    private String mobileNumber;
    private String addressLine;
    private String city;
    private String state;
    private String pincode;
    private String mapUrl;
    private BigDecimal startingPrice;
    private Integer capacityEstimate;
    private List<AmenityAssignmentDto> amenities;
    private Boolean testLead;

    public static PropertyRegistrationPayload fromPublic(CreatePropertyRegistrationRequest request) {
        return PropertyRegistrationPayload.builder()
                .propertyType(request.getPropertyType())
                .propertyName(request.getPropertyName())
                .ownerName(request.getOwnerName())
                .description(request.getDescription())
                .mobileNumber(request.getMobileNumber())
                .addressLine(request.getAddressLine())
                .city(request.getCity())
                .state(request.getState())
                .pincode(request.getPincode())
                .mapUrl(request.getMapUrl())
                .startingPrice(request.getStartingPrice())
                .capacityEstimate(request.getCapacityEstimate())
                .amenities(request.getAmenities())
                .build();
    }

    public static PropertyRegistrationPayload fromAdmin(AdminCreatePropertyRegistrationRequest request) {
        return PropertyRegistrationPayload.builder()
                .propertyType(request.getPropertyType())
                .propertyName(request.getPropertyName())
                .ownerName(request.getOwnerName())
                .description(request.getDescription())
                .mobileNumber(request.getMobileNumber())
                .addressLine(request.getAddressLine())
                .city(request.getCity())
                .state(request.getState())
                .pincode(request.getPincode())
                .mapUrl(request.getMapUrl())
                .startingPrice(request.getStartingPrice())
                .capacityEstimate(request.getCapacityEstimate())
                .amenities(request.getAmenities())
                .testLead(request.getTestLead())
                .build();
    }
}
