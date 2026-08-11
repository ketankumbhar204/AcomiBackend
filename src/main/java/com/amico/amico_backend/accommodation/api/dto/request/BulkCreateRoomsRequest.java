package com.amico.amico_backend.accommodation.api.dto.request;

import com.amico.amico_backend.accommodation.domain.model.AccommodationStatus;
import com.amico.amico_backend.accommodation.domain.model.RoomType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Bulk create rooms under a floor or unit")
public class BulkCreateRoomsRequest {

    @NotNull(message = "Count is required")
    @Min(value = 1, message = "At least one room is required")
    @Max(value = 50, message = "Maximum 50 rooms per bulk operation")
    @Schema(description = "Number of rooms to create", example = "5")
    private Integer count;

    @Schema(description = "Starting room number — server allocates next free block if omitted", example = "201")
    private String startRoomNumber;

    @NotNull(message = "Room type is required")
    @Schema(description = "Room type for all created rooms", implementation = RoomType.class)
    private RoomType roomType;

    @NotNull(message = "Capacity is required")
    @Min(value = 1, message = "Capacity must be at least 1")
    @Schema(description = "Capacity for each room", example = "2")
    private Integer capacity;

    @NotNull(message = "Beds per room is required")
    @Min(value = 0, message = "Beds per room cannot be negative")
    @Schema(description = "Beds to create in each room (0 = rooms only)", example = "2")
    private Integer bedsPerRoom;

    @Schema(description = "Default status for created rooms", implementation = AccommodationStatus.class)
    private AccommodationStatus defaultStatus;

    public AccommodationStatus resolvedDefaultStatus() {
        return defaultStatus == null ? AccommodationStatus.AVAILABLE : defaultStatus;
    }
}
