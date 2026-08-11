package com.amico.amico_backend.accommodation.api.dto.response;

import com.amico.amico_backend.accommodation.domain.model.AccommodationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Lightweight bed list item for progressive loading")
public class BedListItemResponse {

    @Schema(description = "Bed ID")
    private UUID bedId;

    @Schema(description = "Bed label", example = "A")
    private String label;

    @Schema(description = "Bed status", example = "AVAILABLE")
    private AccommodationStatus status;
}
