package com.acomi.acomi_backend.payment.api.dto.request;

import com.acomi.acomi_backend.space.domain.model.PriceTaxMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Update space-level billing and tax settings")
public class UpdateSpaceBillingSettingsRequest {

    @Schema(description = "When false, no tax is applied to generated monthly obligations")
    private boolean taxEnabled;

    @DecimalMin(value = "0.00", message = "Tax rate must be >= 0")
    @DecimalMax(value = "100.00", message = "Tax rate must be <= 100")
    @Schema(description = "Tax rate percent when taxEnabled is true", example = "18.00")
    private BigDecimal taxRatePercent;

    @Schema(description = "EXCLUSIVE = tax added; INCLUSIVE = configured price includes tax")
    private PriceTaxMode priceTaxMode;

    @Size(max = 20)
    @Schema(description = "Optional GSTIN for display on receipts")
    private String gstin;

    @Min(1)
    @Max(28)
    @Schema(description = "Due day of month for full billing periods (default 1)", example = "1")
    private Integer billingDueDay;
}
