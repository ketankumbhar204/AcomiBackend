package com.acomi.acomi_backend.accommodation.api.dto.request;

import com.acomi.acomi_backend.accommodation.domain.model.AccommodationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Bulk create units in a building")
public class BulkCreateUnitsRequest {

    @NotNull(message = "Count is required")
    @Min(value = 1, message = "At least one unit is required")
    @Max(value = 50, message = "Maximum 50 units per bulk operation")
    @Schema(description = "Number of units to create", example = "25")
    private Integer count;

    @Schema(description = "Starting unit number — server allocates next free block if omitted", example = "101")
    private String startUnitNumber;

    @Schema(description = "Default status for created units", implementation = AccommodationStatus.class)
    private AccommodationStatus defaultStatus;

    public AccommodationStatus resolvedDefaultStatus() {
        return defaultStatus == null ? AccommodationStatus.AVAILABLE : defaultStatus;
    }
}
