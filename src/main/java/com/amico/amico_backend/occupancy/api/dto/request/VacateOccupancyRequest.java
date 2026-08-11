package com.amico.amico_backend.occupancy.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Vacate an active occupancy")
public class VacateOccupancyRequest {

    private String remarks;
}
