package com.acomi.acomi_backend.accommodation.api.dto.response;

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
@Schema(description = "Result of duplicating a room")
public class DuplicateRoomResponse {

    @Schema(description = "New room ID")
    private UUID roomId;

    @Schema(description = "New room number")
    private String roomNumber;

    @Schema(description = "Beds created")
    private int bedsCreated;
}
