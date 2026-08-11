package com.amico.amico_backend.accommodation.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Request body for creating a floor")
public class CreateFloorRequest {

    @NotBlank(message = "Floor name is required")
    @Schema(description = "Display name of the floor", example = "Ground Floor")
    private String name;

    @NotNull(message = "Floor number is required")
    @Schema(description = "Numeric floor number for ordering", example = "0")
    private Integer floorNumber;

    @Schema(description = "Sort order for UI display", example = "0")
    private Integer sortOrder;
}
