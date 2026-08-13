package com.acomi.acomi_backend.occupancy.api.dto.response;

import com.acomi.acomi_backend.occupancy.domain.model.AllocationTargetType;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Current member accommodation assignment summary")
public class CurrentOccupancySummaryResponse {

    private UUID occupancyId;
    private OccupancyStatus occupancyStatus;
    private AllocationTargetType targetType;
    private UUID buildingId;
    private String buildingName;
    private UUID floorId;
    private String floorName;
    private UUID unitId;
    private String unitName;
    private UUID roomId;
    private String roomName;
    private UUID bedId;
    private String bedName;
    private LocalDate moveInDate;
}
