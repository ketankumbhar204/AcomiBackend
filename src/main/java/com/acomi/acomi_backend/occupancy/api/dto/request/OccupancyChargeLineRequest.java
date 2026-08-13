package com.acomi.acomi_backend.occupancy.api.dto.request;

import com.acomi.acomi_backend.occupancy.domain.model.OccupancyChargeCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Additional charge captured in the occupancy contract snapshot")
public class OccupancyChargeLineRequest {

    @NotNull
    private OccupancyChargeCode code;

    @NotBlank
    private String label;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal amount;
}
