package com.acomi.acomi_backend.accommodation.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Read-only preview of beds that would receive a fill-empty pricing copy")
public class BedPricingPreviewResponse {

    @Schema(description = "Edited bed plus matching empty beds that would be filled")
    private int affectedBedCount;

    @Schema(description = "Building and floor labels for the affected beds")
    private List<String> affectedLocations;
}
