package com.acomi.acomi_backend.payment.api.dto.response;

import com.acomi.acomi_backend.payment.domain.model.PaymentSettlementAction;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentMethod;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.PaymentSettlementEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentSettlementResponse {

    private UUID settlementId;
    private UUID paymentId;
    private UUID occupancyId;
    private PaymentSettlementAction action;
    private BigDecimal refundAmount;
    private BigDecimal retainedAmount;
    private String reason;
    private UUID refundPaymentId;
    private UUID performedBy;
    private LocalDateTime performedAt;
    private SpacePaymentMethod refundMethod;
    private String refundReference;
    private LocalDate refundDate;
    private String processingStatus;

    public static PaymentSettlementResponse from(PaymentSettlementEntity entity) {
        return PaymentSettlementResponse.builder()
                .settlementId(entity.getId())
                .paymentId(entity.getPayment() != null ? entity.getPayment().getId() : null)
                .occupancyId(entity.getOccupancy() != null ? entity.getOccupancy().getId() : null)
                .action(entity.getAction())
                .refundAmount(entity.getRefundAmount())
                .retainedAmount(entity.getRetainedAmount())
                .reason(entity.getReason())
                .refundPaymentId(
                        entity.getRefundPayment() != null ? entity.getRefundPayment().getId() : null)
                .performedBy(entity.getPerformedBy())
                .performedAt(entity.getPerformedAt())
                .refundMethod(entity.getRefundMethod())
                .refundReference(entity.getRefundReference())
                .refundDate(entity.getRefundDate())
                .processingStatus(entity.getProcessingStatus())
                .build();
    }
}
