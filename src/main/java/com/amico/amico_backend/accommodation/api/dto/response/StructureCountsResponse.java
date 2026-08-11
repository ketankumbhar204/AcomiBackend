package com.amico.amico_backend.accommodation.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Structure counts for a building")
public class StructureCountsResponse {

    @Schema(description = "Active floors", example = "3")
    private long floors;

    @Schema(description = "Active units", example = "0")
    private long units;

    @Schema(description = "Active rooms", example = "30")
    private long rooms;

    @Schema(description = "Active beds", example = "90")
    private long beds;
}
