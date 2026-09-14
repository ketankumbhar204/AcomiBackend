package com.acomi.acomi_backend.space.api.dto.request;

import com.acomi.acomi_backend.space.api.dto.AmenityAssignmentDto;
import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Request body for creating a new space")
public class CreateSpaceRequest {

    @NotBlank(message = "Space name is required")
    @Schema(description = "Display name of the space", example = "Sunrise Apartments")
    private String name;

    @NotNull(message = "Space type is required")
    @Schema(
            description = "Category of the space",
            example = "RENTAL",
            implementation = SpaceType.class)
    private SpaceType type;

    @Schema(description = "Physical address of the space", example = "Pune")
    private String address;

    @Schema(description = "Public contact number for the space", example = "9876543210")
    private String contactNumber;

    @NotNull(message = "Owner ID is required")
    @Schema(description = "UUID of the user who owns this space")
    private UUID ownerId;

    @Schema(description = "When null, defaults to true (owner-created). Converted leads pass false.")
    private Boolean discoverable;

    @Schema(description = "Gender policy when known", implementation = GenderPolicy.class)
    private GenderPolicy genderPolicy;

    @Schema(description = "When true, food is included in rent")
    private Boolean foodIncludedInRent;

    @DecimalMin(value = "-90.0", message = "Latitude is out of range")
    @DecimalMax(value = "90.0", message = "Latitude is out of range")
    private BigDecimal latitude;

    @DecimalMin(value = "-180.0", message = "Longitude is out of range")
    @DecimalMax(value = "180.0", message = "Longitude is out of range")
    private BigDecimal longitude;

    @Valid
    @Schema(description = "Optional amenities for PG, Hostel, or Co-living spaces")
    private List<AmenityAssignmentDto> amenities = new ArrayList<>();
}
