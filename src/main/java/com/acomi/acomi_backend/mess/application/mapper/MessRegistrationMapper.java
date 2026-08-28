package com.acomi.acomi_backend.mess.application.mapper;

import com.acomi.acomi_backend.mess.api.dto.response.MessRegistrationDetailResponse;
import com.acomi.acomi_backend.mess.api.dto.response.MessRegistrationListItemResponse;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;

public final class MessRegistrationMapper {

    private MessRegistrationMapper() {}

    public static MessRegistrationListItemResponse toListItem(MessRegistrationEntity entity) {
        return MessRegistrationListItemResponse.builder()
                .id(entity.getId())
                .reference(entity.getReference())
                .messName(entity.getMessName())
                .ownerName(entity.getOwnerName())
                .mobileNumber(entity.getMobileNumber())
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

    public static MessRegistrationDetailResponse toDetail(MessRegistrationEntity entity) {
        return MessRegistrationDetailResponse.builder()
                .id(entity.getId())
                .reference(entity.getReference())
                .messName(entity.getMessName())
                .ownerName(entity.getOwnerName())
                .mobileNumber(entity.getMobileNumber())
                .mobileVerifiedAt(entity.getMobileVerifiedAt())
                .description(entity.getDescription())
                .addressLine(entity.getAddressLine())
                .city(entity.getCity())
                .state(entity.getState())
                .pincode(entity.getPincode())
                .mapUrl(entity.getMapUrl())
                .monthlyPrice(entity.getMonthlyPrice())
                .mealPrice(entity.getMealPrice())
                .capacityEstimate(entity.getCapacityEstimate())
                .status(entity.getStatus())
                .source(entity.getSource())
                .convertedSpaceId(entity.getConvertedSpaceId())
                .claimedAt(entity.getClaimedAt())
                .claimedVia(entity.getClaimedVia())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .testLead(entity.isTestLead())
                .build();
    }
}
