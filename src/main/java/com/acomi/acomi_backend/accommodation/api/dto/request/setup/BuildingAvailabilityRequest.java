package com.acomi.acomi_backend.accommodation.api.dto.request.setup;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Check whether a building name is available in the current space")
public class BuildingAvailabilityRequest {

    @NotBlank(message = "Building name is required")
    @Schema(example = "B1")
    private String name;
}
