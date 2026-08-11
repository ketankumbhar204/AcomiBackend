package com.amico.amico_backend.accommodation.api.dto.request;

import com.amico.amico_backend.accommodation.domain.model.PropertyLayoutMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Request body for updating a building")
public class UpdateBuildingRequest {

    @NotBlank(message = "Building name is required")
    @Schema(description = "Display name of the building", example = "Building A")
    private String name;

    @Schema(description = "Optional short code", example = "BLD-A")
    private String code;

    @Schema(description = "Property layout mode", implementation = PropertyLayoutMode.class)
    private PropertyLayoutMode layoutMode;
}
