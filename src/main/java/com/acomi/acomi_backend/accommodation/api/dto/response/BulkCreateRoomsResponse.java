package com.acomi.acomi_backend.accommodation.api.dto.response;

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
@Schema(description = "Result of bulk room creation")
public class BulkCreateRoomsResponse {

    @Schema(description = "Rooms created")
    private int roomsCreated;

    @Schema(description = "Beds created")
    private int bedsCreated;

    @Schema(description = "Created room IDs")
    private List<UUID> roomIds;
}
