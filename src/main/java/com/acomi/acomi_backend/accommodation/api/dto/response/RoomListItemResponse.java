package com.acomi.acomi_backend.accommodation.api.dto.response;

import com.acomi.acomi_backend.accommodation.domain.model.RoomType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Lightweight room list item for progressive loading")
public class RoomListItemResponse {

    @Schema(description = "Room ID")
    private UUID roomId;

    @Schema(description = "Room name", example = "Room 101")
    private String name;

    @Schema(description = "Room type", example = "SHARED")
    private RoomType roomType;

    @Schema(description = "Active bed count", example = "3")
    private long bedCount;

    @Schema(description = "Available beds", example = "3")
    private long availableBeds;

    @Schema(description = "Occupied beds", example = "0")
    private long occupiedBeds;
}
