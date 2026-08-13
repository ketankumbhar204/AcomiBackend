package com.acomi.acomi_backend.accommodation.api.dto.response;

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
@Schema(description = "Result of duplicating a floor")
public class DuplicateFloorResponse {

    @Schema(description = "New floor ID")
    private UUID floorId;

    @Schema(description = "New floor number")
    private int floorNumber;

    @Schema(description = "Rooms created")
    private int roomsCreated;

    @Schema(description = "Beds created")
    private int bedsCreated;
}
