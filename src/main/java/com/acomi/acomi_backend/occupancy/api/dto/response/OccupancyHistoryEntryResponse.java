package com.acomi.acomi_backend.occupancy.api.dto.response;

import com.acomi.acomi_backend.occupancy.domain.model.AllocationTargetType;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyHistoryEvent;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyHistoryEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Occupancy lifecycle history entry")
public class OccupancyHistoryEntryResponse {

    private UUID historyId;
    private UUID occupancyId;
    private OccupancyHistoryEvent eventType;
    private AllocationTargetType fromTargetType;
    private UUID fromBuildingId;
    private UUID fromFloorId;
    private UUID fromUnitId;
    private UUID fromRoomId;
    private UUID fromBedId;
    private AllocationTargetType toTargetType;
    private UUID toBuildingId;
    private UUID toFloorId;
    private UUID toUnitId;
    private UUID toRoomId;
    private UUID toBedId;
    private UUID performedBy;
    private LocalDateTime performedAt;
    private String remarks;

    public static OccupancyHistoryEntryResponse from(OccupancyHistoryEntity entity) {
        return OccupancyHistoryEntryResponse.builder()
                .historyId(entity.getId())
                .occupancyId(entity.getOccupancy().getId())
                .eventType(entity.getEventType())
                .fromTargetType(entity.getFromTargetType())
                .fromBuildingId(entity.getFromBuildingId())
                .fromFloorId(entity.getFromFloorId())
                .fromUnitId(entity.getFromUnitId())
                .fromRoomId(entity.getFromRoomId())
                .fromBedId(entity.getFromBedId())
                .toTargetType(entity.getToTargetType())
                .toBuildingId(entity.getToBuildingId())
                .toFloorId(entity.getToFloorId())
                .toUnitId(entity.getToUnitId())
                .toRoomId(entity.getToRoomId())
                .toBedId(entity.getToBedId())
                .performedBy(entity.getPerformedBy().getId())
                .performedAt(entity.getPerformedAt())
                .remarks(entity.getRemarks())
                .build();
    }
}
