package com.acomi.acomi_backend.accommodation.api.dto.request.setup;

import com.acomi.acomi_backend.accommodation.domain.model.PropertyLayoutMode;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Quick setup request for accommodation structure generation")
public class AccommodationSetupRequest {

    @NotNull(message = "Space type is required")
    @Schema(description = "Space type — must match the space", implementation = SpaceType.class)
    private SpaceType spaceType;

    @Schema(
            description = "Property layout mode; defaults from space type when omitted",
            implementation = PropertyLayoutMode.class)
    private PropertyLayoutMode layoutMode;

    @NotNull(message = "Building configuration is required")
    @Valid
    @Schema(description = "Building to create")
    private BuildingSetupInput building;

    @Valid
    @Schema(description = "PG/Hostel floor configuration")
    private PgHostelSetupConfig floors;

    @Valid
    @Schema(description = "Unit configuration for Co-Living or Rental")
    private UnitSetupConfig units;
}
