package com.acomi.acomi_backend.occupancy.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Cancel a reserved occupancy, settle collected payments, and release inventory")
public class CancelReservationRequest {

    private String remarks;

    /**
     * Required for every still-collected payment that has remaining refundable/retainable balance.
     * Unpaid obligations are cancelled automatically when omitted.
     */
    @Valid
    private List<ReservationPaymentSettlementDecision> settlements = new ArrayList<>();
}
