package com.amico.amico_backend.accommodation.api.dto.response;

import com.amico.amico_backend.accommodation.domain.model.AccommodationStatus;
import com.amico.amico_backend.accommodation.domain.model.UnitKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Lightweight unit list item for progressive loading")
public class UnitListItemResponse {

    @Schema(description = "Unit ID")
    private UUID unitId;

    @Schema(description = "Unit name", example = "Unit 101")
    private String name;

    @Schema(description = "Active room count", example = "3")
    private long roomCount;

    @Schema(description = "Active bed count", example = "6")
    private long bedCount;

    @Schema(description = "Available beds in this unit", example = "4")
    private long availableBeds;

    @Schema(description = "Occupied beds in this unit", example = "2")
    private long occupiedBeds;

    @Schema(description = "Unit status (useful for rental leaf units)")
    private AccommodationStatus status;

    @Schema(description = "Whether this is an internal synthetic apartment hidden from UI")
    private boolean synthetic;

    @Schema(description = "Optional apartment classification")
    private UnitKind unitKind;
}
