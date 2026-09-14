package com.acomi.acomi_backend.payment.infrastructure.persistence.entity;

import com.acomi.acomi_backend.common.model.BaseEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.payment.domain.model.PaymentRejectionReason;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentMethod;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import com.acomi.acomi_backend.space.domain.model.PriceTaxMode;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(
        name = "space_payments",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_space_payments_period",
                        columnNames = {
                            "space_id",
                            "member_id",
                            "month",
                            "payment_type",
                            "payment_category",
                            "due_date"
                        }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpacePaymentEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "space_id", nullable = false)
    private SpaceEntity space;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private MemberEntity member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "occupancy_id")
    private OccupancyEntity occupancy;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 20)
    private SpacePaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_category", nullable = false, length = 20)
    private SpacePaymentCategory paymentCategory;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Builder.Default
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "month", nullable = false, length = 7)
    private String month;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private SpacePaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private SpacePaymentStatus paymentStatus;

    @Column(name = "proof_url", columnDefinition = "TEXT")
    private String proofUrl;

    @Column(name = "proof_file_id")
    private UUID proofFileId;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_code", length = 40)
    private PaymentRejectionReason rejectionCode;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "target_label", length = 200)
    private String targetLabel;

    /** Shared id when several meal days were paid with one proof (bulk submit). */
    @Column(name = "payment_batch_id", length = 64)
    private String paymentBatchId;

    /**
     * Immutable human-readable payment reference (e.g. PAY-20260720-000123).
     * Minted once on first submission; never regenerated.
     */
    @Column(name = "payment_reference", length = 32)
    private String paymentReference;

    @Column(name = "billing_period_start")
    private LocalDate billingPeriodStart;

    @Column(name = "billing_period_end")
    private LocalDate billingPeriodEnd;

    @Column(name = "billable_days")
    private Integer billableDays;

    @Column(name = "days_in_month")
    private Integer daysInMonth;

    @Column(name = "configured_monthly_amount", precision = 12, scale = 2)
    private BigDecimal configuredMonthlyAmount;

    @Builder.Default
    @Column(name = "is_prorated", nullable = false)
    private boolean prorated = false;

    @Builder.Default
    @Column(name = "tax_enabled", nullable = false)
    private boolean taxEnabled = false;

    @Column(name = "tax_rate_percent", precision = 5, scale = 2)
    private BigDecimal taxRatePercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_tax_mode", length = 20)
    private PriceTaxMode priceTaxMode;

    @Column(name = "base_amount", precision = 12, scale = 2)
    private BigDecimal baseAmount;

    @Column(name = "tax_amount", precision = 12, scale = 2)
    private BigDecimal taxAmount;
}
