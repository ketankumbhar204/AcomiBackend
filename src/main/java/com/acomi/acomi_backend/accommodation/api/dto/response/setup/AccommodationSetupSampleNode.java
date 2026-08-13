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
@Schema(description = "Sample tree node for quick setup preview")
public class AccommodationSetupSampleNode {

    @Schema(description = "Node type", example = "FLOOR")
    private String type;

    @Schema(description = "Display label", example = "Ground Floor")
    private String label;

    @Schema(description = "Identifier/number", example = "0")
    private String number;

    @Schema(description = "Child nodes")
    private List<AccommodationSetupSampleNode> children;
}
