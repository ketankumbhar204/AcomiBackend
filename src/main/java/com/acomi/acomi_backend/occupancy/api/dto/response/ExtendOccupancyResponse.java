package com.acomi.acomi_backend.occupancy.api.dto.response;

import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExtendOccupancyResponse {

    private OccupancyResponse occupancy;
    private UUID chargeId;
}
