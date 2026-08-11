package com.amico.amico_backend.space.api.dto.request;

import com.amico.amico_backend.space.api.dto.AmenityAssignmentDto;
import com.amico.amico_backend.space.domain.model.SpaceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
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

    @Valid
    @Setter
    @Schema(description = "Optional amenities for PG, Hostel, or Co-living spaces")
    private List<AmenityAssignmentDto> amenities = new ArrayList<>();
}
