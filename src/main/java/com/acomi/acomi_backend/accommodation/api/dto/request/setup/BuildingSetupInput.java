package com.acomi.acomi_backend.accommodation.api.dto.request.setup;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Building details for quick setup")
public class BuildingSetupInput {

    @NotBlank(message = "Building name is required")
    @Schema(description = "Building name", example = "Sunrise PG")
    private String name;

    @Schema(description = "Optional building code", example = "SUN-01")
    private String code;
}
