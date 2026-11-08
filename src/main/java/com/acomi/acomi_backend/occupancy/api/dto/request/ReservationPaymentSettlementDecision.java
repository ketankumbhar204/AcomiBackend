package com.acomi.acomi_backend.occupancy.api.dto.request;

import com.acomi.acomi_backend.payment.domain.model.PaymentSettlementAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Settlement decision for one reservation payment on cancel.")
public class ReservationPaymentSettlementDecision {

    @NotNull
    private UUID paymentId;

    @NotNull
    private PaymentSettlementAction action;

    private BigDecimal refundAmount;

    private BigDecimal retainedAmount;

    private String reason;
}
