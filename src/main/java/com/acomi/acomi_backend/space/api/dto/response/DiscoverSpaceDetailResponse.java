package com.acomi.acomi_backend.space.api.dto.response;

import com.acomi.acomi_backend.property.domain.model.PriceBasis;
import com.acomi.acomi_backend.space.api.dto.AmenityAssignmentDto;
import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Public discovery detail for an active space (no contact or owner fields)")
public class DiscoverSpaceDetailResponse {

    private UUID spaceId;

    @Schema(example = "Sunrise PG")
    private String name;

    @Schema(description = "Space category", example = "PG", implementation = SpaceType.class)
    private SpaceType type;

    @Schema(description = "Flattened address when structured parts are unavailable")
    private String address;

    @Schema(description = "Street / address line when available")
    private String addressLine;

    @Schema(description = "City when available")
    private String city;

    @Schema(description = "State when available")
    private String state;

    @Schema(description = "Pincode when available")
    private String pincode;

    @Schema(description = "Latitude when valid")
    private BigDecimal latitude;

    @Schema(description = "Longitude when valid")
    private BigDecimal longitude;

    @Schema(description = "Existing Google Maps URL when coordinates are unavailable")
    private String mapUrl;

    @Schema(description = "Public listing description when available")
    private String description;

    @Schema(description = "Listing start-from price when greater than zero")
    private BigDecimal startingPrice;

    @Schema(description = "How startingPrice is quoted for property listings", implementation = PriceBasis.class)
    private PriceBasis priceBasis;

    @Schema(description = "Mess monthly start-from price when greater than zero")
    private BigDecimal monthlyPrice;

    @Schema(description = "Mess per-meal price when greater than zero")
    private BigDecimal mealPrice;

    @Schema(description = "Sharing options as listing information, not inventory")
    private String sharingNotes;

    @Schema(description = "Amenity codes for filtering/icons")
    private List<String> amenityCodes;

    @Schema(description = "Amenity display labels for UI")
    private List<String> amenityLabels;

    @Schema(description = "Full amenity assignments with code and label")
    private List<AmenityAssignmentDto> amenities;

    @Schema(description = "When true, food is included in rent")
    private boolean foodIncludedInRent;

    @Schema(description = "Gender policy when set", implementation = GenderPolicy.class)
    private GenderPolicy genderPolicy;

    @Schema(description = "True if the caller has an ACTIVE membership in this space")
    private boolean alreadyMember;

    @Schema(description = "True when this Space was converted from a test-lead registration")
    private boolean testSpace;

    @Schema(description = "True if the caller is the Space owner. Used to block self-enquiry.")
    private boolean ownedByCurrentUser;
}
