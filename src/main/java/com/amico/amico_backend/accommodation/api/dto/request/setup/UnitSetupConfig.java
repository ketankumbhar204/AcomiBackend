package com.amico.amico_backend.accommodation.api.dto.request.setup;

import com.amico.amico_backend.accommodation.domain.model.AccommodationStatus;
import com.amico.amico_backend.accommodation.domain.model.RoomType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Unit configuration for Co-Living or Rental quick setup")
public class UnitSetupConfig {

    @NotNull(message = "Unit count is required")
    @Min(value = 1, message = "At least one unit is required")
    @Schema(description = "Number of units", example = "10")
    private Integer count;

    @Schema(description = "Starting unit number", example = "101")
    private String startNumber;

    @Schema(description = "Increment between unit numbers (Co-Living)", example = "1")
    private Integer numberingStep;

    @Schema(description = "Rooms per unit (Co-Living)", example = "3")
    private Integer roomsPerUnit;

    @Schema(description = "Beds per room (Co-Living)", example = "2")
    private Integer bedsPerRoom;

    @Schema(description = "Default room type (Co-Living)", implementation = RoomType.class)
    private RoomType defaultRoomType;

    @Schema(description = "Capacity per room (Co-Living)", example = "2")
    private Integer capacityPerRoom;

    @Schema(description = "Default unit status (Rental)", implementation = AccommodationStatus.class)
    private AccommodationStatus defaultStatus;

    public String resolvedStartNumber() {
        return startNumber == null || startNumber.isBlank() ? "101" : startNumber;
    }

    public int resolvedNumberingStep() {
        return numberingStep == null ? 1 : numberingStep;
    }

    public AccommodationStatus resolvedDefaultStatus() {
        return defaultStatus == null ? AccommodationStatus.AVAILABLE : defaultStatus;
    }
}
