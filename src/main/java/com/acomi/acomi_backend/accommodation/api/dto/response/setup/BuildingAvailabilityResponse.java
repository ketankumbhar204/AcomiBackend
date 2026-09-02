package com.acomi.acomi_backend.accommodation.api.dto.response.setup;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Whether an active building name is already used in the space")
public class BuildingAvailabilityResponse {

    @Schema(description = "True when no active building in this space uses the name")
    private final boolean nameAvailable;

    @Schema(description = "Present when the name is not available")
    private final String message;
}
