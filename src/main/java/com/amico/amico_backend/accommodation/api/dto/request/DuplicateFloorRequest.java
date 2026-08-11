package com.amico.amico_backend.accommodation.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Request to duplicate a floor with its rooms and beds")
public class DuplicateFloorRequest {

    @NotNull(message = "Target floor number is required")
    @Schema(description = "Floor number for the new floor", example = "2")
    private Integer targetFloorNumber;

    @Schema(description = "Display name — auto-generated if omitted", example = "Floor 2")
    private String targetName;

    @Schema(description = "Renumber rooms using floor-prefix formula", example = "true")
    private Boolean renumberRooms = true;

    public boolean isRenumberRooms() {
        return renumberRooms == null || renumberRooms;
    }
}
