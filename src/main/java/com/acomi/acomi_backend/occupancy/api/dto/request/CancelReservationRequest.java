package com.acomi.acomi_backend.occupancy.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Cancel a reserved occupancy and release inventory")
public class CancelReservationRequest {

    private String remarks;
}
