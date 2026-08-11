package com.amico.amico_backend.member.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Request body for updating a member's deposit details")
public class UpdateDepositRequest {

    @NotNull(message = "Deposit amount is required")
    @DecimalMin(value = "0", message = "Deposit amount must be zero or greater")
    @Schema(description = "Total deposit amount required", example = "10000.00")
    private BigDecimal depositAmount;

    @NotNull(message = "Deposit paid is required")
    @DecimalMin(value = "0", message = "Deposit paid must be zero or greater")
    @Schema(description = "Amount of deposit collected", example = "10000.00")
    private BigDecimal depositPaid;

    @NotNull(message = "Deposit refunded is required")
    @DecimalMin(value = "0", message = "Deposit refunded must be zero or greater")
    @Schema(description = "Amount of deposit refunded", example = "0.00")
    private BigDecimal depositRefunded;
}
