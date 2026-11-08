package com.acomi.acomi_backend.payment.api.dto.request;

import com.acomi.acomi_backend.payment.domain.model.PaymentSettlementAction;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Settle a collected payment (refund and/or retain). Not for unpaid cancel.")
public class SettleSpacePaymentRequest {

    @NotNull
    private PaymentSettlementAction action;

    @Schema(description = "Required for PARTIAL_REFUND. Ignored for FULL_REFUND (uses remaining refundable).")
    private BigDecimal refundAmount;

    @Schema(description = "Optional retain amount. Payment-level partial refunds default retain to 0.")
    private BigDecimal retainedAmount;

    @Schema(description = "Reason for refund and/or retention.")
    private String reason;

    @Schema(description = "Refund payout method when recording a refund.")
    private SpacePaymentMethod refundMethod;

    @Schema(description = "Refund date; defaults to today when omitted.")
    private LocalDate refundDate;

    @Schema(description = "Bank/UPI/cheque reference for the refund payout.")
    private String refundReference;
}
