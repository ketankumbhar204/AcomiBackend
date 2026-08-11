package com.amico.amico_backend.accommodation.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Request to duplicate a building with its full structure")
public class DuplicateBuildingRequest {

    @NotBlank(message = "Target building name is required")
    @Schema(description = "Name for the new building", example = "Building B")
    private String targetBuildingName;

    @Schema(description = "Optional code for the new building", example = "BLD-B")
    private String targetBuildingCode;
}
