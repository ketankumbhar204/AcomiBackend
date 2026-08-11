package com.amico.amico_backend.accommodation.api.dto.response;

import com.amico.amico_backend.accommodation.domain.model.AccommodationStatus;
import com.amico.amico_backend.occupancy.domain.model.AllocationTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Selectable accommodation target for occupancy allocation, transfer, or reservation")
public class AllocationTargetSearchResponse {

    private AllocationTargetType targetType;
    private UUID targetId;

    private UUID buildingId;
    private String buildingName;

    private UUID floorId;
    private String floorName;

    private UUID unitId;
    private String unitName;

    private UUID roomId;
    private String roomName;
    private String roomNumber;

    private UUID bedId;
    private String bedName;
    private String bedNumber;

    @Schema(description = "Human-readable full path, e.g. Building A · Floor 1 · Room 101 · Bed A")
    private String displayPath;

    @Schema(description = "Compact path for mobile list rows")
    private String displayPathShort;

    private AccommodationStatus status;
    private BigDecimal defaultRent;
    private BigDecimal defaultDeposit;

    @Schema(description = "Whether this target can be selected for a new allocation or reservation")
    private boolean selectable;

    @Schema(description = "Reason when selectable is false")
    private String notSelectableReason;
}
