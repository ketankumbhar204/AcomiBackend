package com.amico.amico_backend.accommodation.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Result of duplicating a building")
public class DuplicateBuildingResponse {

    @Schema(description = "New building ID")
    private UUID buildingId;

    @Schema(description = "New building name")
    private String name;

    @Schema(description = "New building code")
    private String code;

    @Schema(description = "Floors created")
    private int floorsCreated;

    @Schema(description = "Units created")
    private int unitsCreated;

    @Schema(description = "Rooms created")
    private int roomsCreated;

    @Schema(description = "Beds created")
    private int bedsCreated;
}
