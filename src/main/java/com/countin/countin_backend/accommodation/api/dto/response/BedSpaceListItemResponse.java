package com.countin.countin_backend.accommodation.api.dto.response;

import com.countin.countin_backend.accommodation.domain.model.AccommodationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Space-scoped bed list item with location context for dashboard drill-down")
public class BedSpaceListItemResponse {

    private UUID bedId;
    private String label;
    private AccommodationStatus status;
    private UUID buildingId;
    private String buildingName;
    private UUID floorId;
    private String floorName;
    private UUID unitId;
    private String unitName;
    private UUID roomId;
    private String roomName;
}
