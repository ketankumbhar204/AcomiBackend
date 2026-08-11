package com.amico.amico_backend.payment.api.dto.response;

import com.amico.amico_backend.payment.domain.model.PaymentTimelineEventType;
import com.amico.amico_backend.payment.infrastructure.persistence.entity.SpacePaymentTimelineEventEntity;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentTimelineEventResponse {

    private UUID eventId;
    private UUID paymentId;
    private PaymentTimelineEventType eventType;
    private LocalDateTime performedAt;
    private String remarks;
    private UUID performedBy;

    public static PaymentTimelineEventResponse from(SpacePaymentTimelineEventEntity entity) {
        return PaymentTimelineEventResponse.builder()
                .eventId(entity.getId())
                .paymentId(entity.getPayment().getId())
                .eventType(entity.getEventType())
                .performedAt(entity.getPerformedAt())
                .remarks(entity.getRemarks())
                .performedBy(entity.getPerformedBy())
                .build();
    }
}
