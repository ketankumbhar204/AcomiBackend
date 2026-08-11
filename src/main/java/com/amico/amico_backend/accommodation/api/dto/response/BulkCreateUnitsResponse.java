package com.amico.amico_backend.accommodation.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Result of bulk unit creation")
public class BulkCreateUnitsResponse {

    @Schema(description = "Units created")
    private int unitsCreated;

    @Schema(description = "Created unit IDs")
    private List<UUID> unitIds;
}
