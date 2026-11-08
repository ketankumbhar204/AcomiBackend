package com.acomi.acomi_backend.payment.infrastructure.persistence.entity;

import com.acomi.acomi_backend.common.model.BaseEntity;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.payment.domain.model.PaymentSettlementAction;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentMethod;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payment_settlements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentSettlementEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "space_id", nullable = false)
    private SpaceEntity space;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private SpacePaymentEntity payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "occupancy_id")
    private OccupancyEntity occupancy;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 30)
    private PaymentSettlementAction action;

    @Builder.Default
    @Column(name = "refund_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "retained_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal retainedAmount = BigDecimal.ZERO;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_payment_id")
    private SpacePaymentEntity refundPayment;

    @Column(name = "performed_by")
    private UUID performedBy;

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_method", length = 20)
    private SpacePaymentMethod refundMethod;

    @Column(name = "refund_reference", length = 100)
    private String refundReference;

    @Column(name = "refund_date")
    private LocalDate refundDate;

    /** Only COMPLETED refunds reduce net collected. */
    @Builder.Default
    @Column(name = "processing_status", nullable = false, length = 20)
    private String processingStatus = "COMPLETED";
}
