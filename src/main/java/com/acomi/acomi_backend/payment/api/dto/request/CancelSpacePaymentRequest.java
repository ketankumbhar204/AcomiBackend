package com.acomi.acomi_backend.payment.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Cancel an unpaid payment obligation. Paid payments must be settled instead.")
public class CancelSpacePaymentRequest {

    private String reason;
}
