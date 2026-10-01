package com.acomi.acomi_backend.accommodation.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Proposed rent/deposit used to preview fill-empty matching beds")
public class PreviewBedPricingRequest {

    @DecimalMin(value = "0.0", inclusive = true)
    @Schema(description = "Proposed default monthly rent")
    private BigDecimal defaultRent;

    @DecimalMin(value = "0.0", inclusive = true)
    @Schema(description = "Proposed default deposit")
    private BigDecimal defaultDeposit;
}
