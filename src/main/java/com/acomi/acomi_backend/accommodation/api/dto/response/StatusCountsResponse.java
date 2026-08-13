package com.acomi.acomi_backend.accommodation.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Status breakdown aggregated across rooms, beds, and units")
public class StatusCountsResponse {

    @Schema(description = "Available count", example = "90")
    private long available;

    @Schema(description = "Occupied count", example = "0")
    private long occupied;

    @Schema(description = "Reserved count", example = "0")
    private long reserved;

    @Schema(description = "Maintenance count", example = "0")
    private long maintenance;

    @Schema(description = "Blocked count", example = "0")
    private long blocked;
}
