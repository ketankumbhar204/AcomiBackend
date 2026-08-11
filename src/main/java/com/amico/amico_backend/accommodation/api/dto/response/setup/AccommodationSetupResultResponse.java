package com.amico.amico_backend.accommodation.api.dto.response.setup;

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
@Schema(description = "Quick setup execution result")
public class AccommodationSetupResultResponse {

    @Schema(description = "Created building ID")
    private UUID buildingId;

    @Schema(description = "Aggregate totals")
    private AccommodationSetupTotals totals;

    @Schema(description = "True when returning a cached result for the same idempotency key")
    private boolean idempotentReplay;
}
