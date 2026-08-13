package com.acomi.acomi_backend.occupancy.api.dto.response;

import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Brief occupant summary for bed/room detail views")
public class BedOccupantSummaryResponse {

    private UUID occupancyId;
    private UUID memberId;
    private String memberName;
    private OccupancyStatus occupancyStatus;
}
