package com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity;

import com.acomi.acomi_backend.common.model.BaseEntity;
import com.acomi.acomi_backend.inquirycredit.domain.model.InquiryCreditPurchaseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "inquiry_credit_purchase_requests",
        indexes = {
            @Index(
                    name = "idx_inquiry_purchase_status_requested",
                    columnList = "status, requested_at DESC"),
            @Index(
                    name = "idx_inquiry_purchase_user_status",
                    columnList = "user_id, status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InquiryCreditPurchaseRequestEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "package_id", nullable = false)
    private UUID packageId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "credits", nullable = false)
    private int credits;

    @Column(name = "payment_method", nullable = false, length = 40)
    @Builder.Default
    private String paymentMethod = "UPI_MANUAL";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InquiryCreditPurchaseStatus status;

    @Column(name = "utr", length = 80)
    private String utr;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "verified_by_user_id")
    private UUID verifiedByUserId;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;
}
