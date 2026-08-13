package com.acomi.acomi_backend.payment.api.dto.response;

import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentTimelineResponse {

    private UUID paymentId;
    private List<PaymentTimelineEventResponse> events;
}
