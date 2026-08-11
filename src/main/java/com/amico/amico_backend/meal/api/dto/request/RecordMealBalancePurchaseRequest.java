package com.amico.amico_backend.meal.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Create or renew a member meal subscription")
public class RecordMealBalancePurchaseRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Schema(description = "Meals count or currency amount depending on space balance unit")
    private BigDecimal amount;

    @DecimalMin(value = "0.01", message = "Paid amount must be greater than zero")
    @Schema(
            description =
                    "Money collected from the member for this purchase. Required when balance unit is MEALS; defaults to amount when unit is CURRENCY.")
    private BigDecimal paidAmount;

    @Schema(description = "Optional note for the subscription")
    private String remarks;

    @Schema(
            description =
                    "When true or omitted, replaces the active subscription pack. Set false only for legacy cumulative top-ups.")
    private Boolean replaceBalance;

    @Schema(description = "Last day of the subscription period. Defaults to end of current month.")
    private LocalDate validTill;
}
