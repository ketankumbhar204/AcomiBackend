package com.acomi.acomi_backend.payment.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Owner/manager marks a pending or under-review payment as received")
public class MarkPaymentReceivedRequest {

    private String remarks;
}
