package com.amico.amico_backend.accommodation.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Request to duplicate a room with its beds")
public class DuplicateRoomRequest {

    @Schema(description = "Target room number — server suggests next free if omitted", example = "102")
    private String targetRoomNumber;
}
