package com.amico.amico_backend.accommodation.api.dto.response.setup;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Aggregate counts for a quick setup operation")
public class AccommodationSetupTotals {

    @Schema(description = "Number of floors created", example = "3")
    private int floors;

    @Schema(description = "Number of units created", example = "10")
    private int units;

    @Schema(description = "Number of rooms created", example = "30")
    private int rooms;

    @Schema(description = "Number of beds created", example = "90")
    private int beds;
}
