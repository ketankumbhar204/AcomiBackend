package com.acomi.acomi_backend.accommodation.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Lightweight floor list item for progressive loading")
public class FloorListItemResponse {

    @Schema(description = "Floor ID")
    private UUID floorId;

    @Schema(description = "Floor name", example = "Floor 1")
    private String name;

    @Schema(description = "Active room count", example = "20")
    private long roomCount;

    @Schema(description = "Active bed count", example = "60")
    private long bedCount;

    @Schema(description = "Available beds", example = "60")
    private long available;

    @Schema(description = "Occupied beds", example = "0")
    private long occupied;
}
