package com.acomi.acomi_backend.accommodation.api.dto.response.setup;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Quick setup preview — no persistence")
public class AccommodationSetupPreviewResponse {

    @Schema(description = "Aggregate totals")
    private AccommodationSetupTotals totals;

    @Schema(description = "Sample structure snippet")
    private List<AccommodationSetupSampleNode> sample;

    @Schema(description = "Non-blocking warnings")
    private List<String> warnings;
}
