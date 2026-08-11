package com.amico.amico_backend.space.api.dto.response;

import com.amico.amico_backend.space.api.dto.AmenityAssignmentDto;
import com.amico.amico_backend.space.domain.model.Space;
import com.amico.amico_backend.space.domain.model.MealBillingType;
import com.amico.amico_backend.space.domain.model.PrepaidBalanceUnit;
import com.amico.amico_backend.space.domain.model.SpaceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Complete details of a space")
public class SpaceDetailsResponse {

    private UUID id;

    @Schema(example = "Sunrise PG")
    private String name;

    @Schema(description = "Space category", example = "PG", implementation = SpaceType.class)
    private SpaceType type;

    private String address;
    private String contactNumber;
    private UUID ownerId;

    @Schema(description = "When true, food is mandatory and included in rent (no separate food charge line)")
    private boolean foodIncludedInRent;

    @Schema(description = "Default monthly food charge prefill when food is billed separately")
    private BigDecimal defaultFoodCharge;

    @Schema(description = "Meal billing mode for Mess / meal participation")
    private MealBillingType mealBillingType;

    @Schema(description = "Prepaid balance unit when mealBillingType is PREPAID_BALANCE")
    private PrepaidBalanceUnit prepaidBalanceUnit;

    @Schema(description = "When prepaid balance is exhausted, bill pay-per-meal automatically")
    private boolean prepaidFallbackToPayPerMeal;

    @Schema(description = "Amenities offered at this space (PG, Hostel, Co-living)")
    private List<AmenityAssignmentDto> amenities;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SpaceDetailsResponse from(Space space) {
        return from(space, List.of());
    }

    public static SpaceDetailsResponse from(Space space, List<AmenityAssignmentDto> amenities) {
        return SpaceDetailsResponse.builder()
                .id(space.getId())
                .name(space.getName())
                .type(space.getType())
                .address(space.getAddress())
                .contactNumber(space.getContactNumber())
                .ownerId(space.getOwnerId())
                .foodIncludedInRent(space.isFoodIncludedInRent())
                .defaultFoodCharge(space.getDefaultFoodCharge())
                .mealBillingType(space.getMealBillingType())
                .prepaidBalanceUnit(space.getPrepaidBalanceUnit())
                .prepaidFallbackToPayPerMeal(space.isPrepaidFallbackToPayPerMeal())
                .amenities(amenities)
                .createdAt(space.getCreatedAt())
                .updatedAt(space.getUpdatedAt())
                .build();
    }
}
