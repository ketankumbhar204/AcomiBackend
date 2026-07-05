package com.countin.countin_backend.space.application.mapper;

import com.countin.countin_backend.space.api.dto.AmenityAssignmentDto;
import com.countin.countin_backend.space.api.dto.request.UpdateSpaceRequest;
import com.countin.countin_backend.space.api.dto.response.SpaceDetailsResponse;
import com.countin.countin_backend.space.api.dto.response.SpaceResponse;
import com.countin.countin_backend.space.domain.model.Space;
import com.countin.countin_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.util.List;

public final class SpaceMapper {

    private SpaceMapper() {}

    public static Space toDomain(SpaceEntity entity) {
        return Space.builder()
                .id(entity.getId())
                .name(entity.getName())
                .type(entity.getType())
                .address(entity.getAddress())
                .contactNumber(entity.getContactNumber())
                .ownerId(entity.getOwner().getId())
                .active(entity.isActive())
                .defaultFoodCharge(entity.getDefaultFoodCharge())
                .foodIncludedInRent(entity.isFoodIncludedInRent())
                .mealBillingType(entity.getMealBillingType())
                .prepaidBalanceUnit(entity.getPrepaidBalanceUnit())
                .prepaidFallbackToPayPerMeal(entity.isPrepaidFallbackToPayPerMeal())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static Space applyUpdate(Space space, UpdateSpaceRequest request) {
        Space.SpaceBuilder builder = space.toBuilder()
                .name(request.getName())
                .address(request.getAddress())
                .contactNumber(request.getContactNumber())
                .defaultFoodCharge(request.getDefaultFoodCharge());
        if (request.getFoodIncludedInRent() != null) {
            builder.foodIncludedInRent(request.getFoodIncludedInRent());
        }
        return builder.build();
    }

    public static void applyToEntity(SpaceEntity entity, Space space) {
        entity.setName(space.getName());
        entity.setAddress(space.getAddress());
        entity.setContactNumber(space.getContactNumber());
        entity.setDefaultFoodCharge(space.getDefaultFoodCharge());
        entity.setFoodIncludedInRent(space.isFoodIncludedInRent());
        entity.setMealBillingType(space.getMealBillingType());
        entity.setPrepaidBalanceUnit(space.getPrepaidBalanceUnit());
        entity.setPrepaidFallbackToPayPerMeal(space.isPrepaidFallbackToPayPerMeal());
    }

    public static SpaceDetailsResponse toDetailsResponse(Space space, List<AmenityAssignmentDto> amenities) {
        return SpaceDetailsResponse.from(space, amenities);
    }

    public static SpaceResponse toCreateResponse(SpaceEntity entity) {
        return SpaceResponse.from(entity);
    }
}
