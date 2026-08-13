package com.acomi.acomi_backend.meal.infrastructure.persistence.entity;

import com.acomi.acomi_backend.common.model.BaseEntity;
import com.acomi.acomi_backend.meal.domain.model.MealPollPaymentChoice;
import com.acomi.acomi_backend.meal.domain.model.MealPollPaymentStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "meal_poll_day_payments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"space_id", "member_id", "poll_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealPollDayPaymentEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "space_id", nullable = false)
    private SpaceEntity space;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private MemberEntity member;

    @Column(name = "poll_date", nullable = false)
    private LocalDate pollDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_choice", nullable = false, length = 20)
    private MealPollPaymentChoice paymentChoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private MealPollPaymentStatus paymentStatus;

    @Column(name = "proof_image_url", columnDefinition = "TEXT")
    private String proofImageUrl;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private SpacePaymentMethod paymentMethod;

    @Column(name = "proof_submitted_at")
    private java.time.LocalDateTime proofSubmittedAt;

    @Column(name = "proof_reviewed_at")
    private java.time.LocalDateTime proofReviewedAt;

    @Column(name = "proof_reviewed_by")
    private java.util.UUID proofReviewedBy;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "prepaid_overflow_amount", precision = 12, scale = 2)
    private BigDecimal prepaidOverflowAmount;

    @Column(name = "prepaid_debited_amount", precision = 12, scale = 2)
    private BigDecimal prepaidDebitedAmount;

    @Builder.Default
    @Column(name = "prepaid_overflow_payment", nullable = false)
    private boolean prepaidOverflowPayment = false;

    /** Current meal total for this member/day (pay-per-meal snapshot). */
    @Column(name = "charged_amount", precision = 12, scale = 2)
    private BigDecimal chargedAmount;

    /**
     * Optional bulk-payment reference when multiple day payments share one proof upload.
     * Example: MP-20260713-001
     */
    @Column(name = "payment_batch_id", length = 64)
    private String paymentBatchId;

    /**
     * Immutable human-readable payment reference (e.g. PAY-20260720-000123).
     * Minted once on first submission; never regenerated.
     */
    @Column(name = "payment_reference", length = 32)
    private String paymentReference;
}
