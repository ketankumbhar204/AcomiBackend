package com.amico.amico_backend.accommodation.api.dto.request.setup;

import com.amico.amico_backend.accommodation.domain.model.RoomType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Floor/room/bed configuration for PG and Hostel quick setup")
public class PgHostelSetupConfig {

    @NotNull(message = "Floor count is required")
    @Min(value = 1, message = "At least one floor is required")
    @Max(value = 20, message = "Maximum 20 floors per setup")
    @Schema(description = "Number of floors", example = "3")
    private Integer count;

    @Schema(description = "Whether the first floor is ground floor (floor number 0)", example = "true")
    private Boolean includeGroundFloor = true;

    @Min(value = 1, message = "At least one apartment per floor is required")
    @Schema(description = "Apartments created on each floor (APARTMENT_PG only)", example = "4")
    private Integer apartmentsPerFloor;

    @NotNull(message = "Rooms per floor is required")
    @Min(value = 1, message = "At least one room per floor is required")
    @Schema(description = "Rooms per floor (CORRIDOR_PG) or rooms per apartment (APARTMENT_PG)", example = "10")
    private Integer roomsPerFloor;

    @NotNull(message = "Beds per room is required")
    @Min(value = 1, message = "At least one bed per room is required")
    @Schema(description = "Beds created in each room", example = "3")
    private Integer bedsPerRoom;

    @NotNull(message = "Default room type is required")
    @Schema(description = "Room type applied to all generated rooms", implementation = RoomType.class)
    private RoomType defaultRoomType;

    @NotNull(message = "Capacity per room is required")
    @Min(value = 1, message = "Capacity must be at least 1")
    @Schema(description = "Capacity for each generated room", example = "3")
    private Integer capacityPerRoom;

    public boolean isIncludeGroundFloor() {
        return includeGroundFloor == null || includeGroundFloor;
    }
}
