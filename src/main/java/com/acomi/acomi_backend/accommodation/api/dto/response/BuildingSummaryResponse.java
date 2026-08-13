package com.acomi.acomi_backend.accommodation.api.dto.response;

import com.acomi.acomi_backend.accommodation.domain.model.PropertyLayoutMode;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
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
@Schema(description = "Building summary for Manual Builder header")
public class BuildingSummaryResponse {

    @Schema(description = "Building ID")
    private UUID buildingId;

    @Schema(description = "Building name")
    private String name;

    @Schema(description = "Building code")
    private String code;

    @Schema(description = "Space ID")
    private UUID spaceId;

    @Schema(description = "Property layout mode for this building")
    private PropertyLayoutMode layoutMode;

    @Schema(description = "Total active apartments including synthetic")
    private long unitCount;

    @Schema(description = "Visible (non-synthetic) active apartments")
    private long visibleUnitCount;

    @Schema(description = "Synthetic internal apartments")
    private long syntheticUnitCount;

    @JsonUnwrapped
    @Schema(description = "Structure counts")
    private StructureCountsResponse counts;

    @JsonUnwrapped
    @Schema(description = "Aggregated status counts")
    private StatusCountsResponse statusCounts;

    @JsonUnwrapped
    @Schema(description = "Availability breakdown by beds, rooms, and units")
    private AvailabilityCountsResponse availability;
}
