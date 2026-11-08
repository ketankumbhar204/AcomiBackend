package com.acomi.acomi_backend.payment.api.dto.response;

import com.acomi.acomi_backend.occupancy.domain.model.StayKind;
import com.acomi.acomi_backend.payment.application.support.PaymentDueStatusCalculator;
import com.acomi.acomi_backend.payment.application.support.PaymentDueStatusCalculator.DueStatus;
import com.acomi.acomi_backend.payment.domain.model.PaymentFinancialDisposition;
import com.acomi.acomi_backend.payment.domain.model.PaymentRejectionReason;
import com.acomi.acomi_backend.payment.domain.model.PaymentSettlementStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentMethod;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.acomi.acomi_backend.space.domain.model.PriceTaxMode;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SpacePaymentResponse {

    private UUID paymentId;
    private UUID spaceId;
    private UUID memberId;
    private String memberName;
    private String memberMobile;
    private UUID occupancyId;

    /** Original obligation when this row is an explicit monthly-rent reissue. */
    private UUID replacesPaymentId;
    /**
     * Stay type from the linked occupancy, when present.
     * Null when the payment has no occupancy link or the occupancy has a legacy null stayKind.
     * Never inferred from amount/category.
     */
    private StayKind stayKind;
    private SpacePaymentType paymentType;
    private SpacePaymentCategory paymentCategory;
    private String title;
    private String description;
    private BigDecimal amount;
    private String currencyCode;
    private LocalDate dueDate;
    private String month;
    private SpacePaymentMethod paymentMethod;
    private SpacePaymentStatus paymentStatus;
    private String proofUrl;
    private java.util.UUID proofFileId;
    private String referenceNumber;
    private String remarks;
    private String rejectionReason;
    private PaymentRejectionReason rejectionCode;
    private UUID reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDate paymentDate;
    private String targetLabel;
    private String paymentBatchId;
    private String paymentReference;
    private List<LocalDate> mealDates;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private LocalDate billingPeriodStart;
    private LocalDate billingPeriodEnd;
    private Integer billableDays;
    private Integer daysInMonth;
    private Boolean isProrated;
    private BigDecimal configuredMonthlyAmount;
    private Boolean taxEnabled;
    private BigDecimal taxRatePercent;
    private PriceTaxMode priceTaxMode;
    private BigDecimal baseAmount;
    private BigDecimal taxAmount;
    private BigDecimal paidAmount;
    private BigDecimal outstandingAmount;
    private PaymentSettlementStatus settlementStatus;
    private Boolean isOverdue;
    private Integer daysOverdue;
    private Boolean reminderEligible;
    /** Completed cash refunds against this obligation. */
    private BigDecimal refundedAmount;
    /** Explicitly retained collected amount (not refunded). */
    private BigDecimal retainedAmount;
    /** Collected − completed refunds. */
    private BigDecimal netCollectedAmount;
    private Boolean partiallyRefunded;
    private PaymentFinancialDisposition financialDisposition;

    public static SpacePaymentResponse from(SpacePaymentEntity entity) {
        LocalDate today = entity.getSpace() != null
                ? PaymentDueStatusCalculator.businessDate(entity.getSpace())
                : LocalDate.now();
        return from(entity, null, today);
    }

    public static SpacePaymentResponse from(SpacePaymentEntity entity, List<LocalDate> mealDates) {
        LocalDate today = entity.getSpace() != null
                ? PaymentDueStatusCalculator.businessDate(entity.getSpace())
                : LocalDate.now();
        return from(entity, mealDates, today);
    }

    public static SpacePaymentResponse from(
            SpacePaymentEntity entity, List<LocalDate> mealDates, LocalDate businessDate) {
        return from(entity, mealDates, businessDate, entity.getProofUrl());
    }

    public static SpacePaymentResponse from(
            SpacePaymentEntity entity,
            List<LocalDate> mealDates,
            LocalDate businessDate,
            String resolvedProofUrl) {
        DueStatus due = PaymentDueStatusCalculator.calculate(entity, businessDate);
        BigDecimal refunded = entity.getRefundedAmount() != null
                ? entity.getRefundedAmount()
                : BigDecimal.ZERO;
        BigDecimal retained = entity.getRetainedAmount() != null
                ? entity.getRetainedAmount()
                : BigDecimal.ZERO;
        BigDecimal collected = due.getPaidAmount() != null ? due.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal netCollected = collected.subtract(refunded).max(BigDecimal.ZERO);
        boolean partiallyRefunded = refunded.signum() > 0
                && entity.getPaymentStatus() != SpacePaymentStatus.REFUNDED
                && entity.getPaymentStatus() != SpacePaymentStatus.CANCELLED;
        PaymentFinancialDisposition disposition = resolveDisposition(entity, refunded, retained);
        return SpacePaymentResponse.builder()
                .paymentId(entity.getId())
                .spaceId(entity.getSpace().getId())
                .memberId(entity.getMember().getId())
                .memberName(entity.getMember().getFullName())
                .memberMobile(entity.getMember().getMobileNumber())
                .occupancyId(entity.getOccupancy() != null ? entity.getOccupancy().getId() : null)
                .replacesPaymentId(entity.getRelatedPayment() != null
                        ? entity.getRelatedPayment().getId()
                        : null)
                .stayKind(entity.getOccupancy() != null ? entity.getOccupancy().getStayKind() : null)
                .paymentType(entity.getPaymentType())
                .paymentCategory(entity.getPaymentCategory())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .amount(entity.getAmount())
                .currencyCode(entity.getCurrencyCode())
                .dueDate(entity.getDueDate())
                .month(entity.getMonth())
                .paymentMethod(entity.getPaymentMethod())
                .paymentStatus(entity.getPaymentStatus())
                .proofUrl(resolvedProofUrl)
                .proofFileId(entity.getProofFileId())
                .referenceNumber(entity.getReferenceNumber())
                .remarks(entity.getRemarks())
                .rejectionReason(entity.getRejectionReason())
                .rejectionCode(entity.getRejectionCode())
                .reviewedBy(entity.getReviewedBy())
                .reviewedAt(entity.getReviewedAt())
                .paymentDate(entity.getPaymentDate())
                .targetLabel(entity.getTargetLabel())
                .paymentBatchId(entity.getPaymentBatchId())
                .paymentReference(entity.getPaymentReference())
                .mealDates(mealDates)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .billingPeriodStart(entity.getBillingPeriodStart())
                .billingPeriodEnd(entity.getBillingPeriodEnd())
                .billableDays(entity.getBillableDays())
                .daysInMonth(entity.getDaysInMonth())
                .isProrated(entity.isProrated())
                .configuredMonthlyAmount(entity.getConfiguredMonthlyAmount())
                .taxEnabled(entity.isTaxEnabled())
                .taxRatePercent(entity.getTaxRatePercent())
                .priceTaxMode(entity.getPriceTaxMode())
                .baseAmount(entity.getBaseAmount())
                .taxAmount(entity.getTaxAmount())
                .paidAmount(due.getPaidAmount())
                .outstandingAmount(due.getOutstandingAmount())
                .settlementStatus(due.getSettlementStatus())
                .isOverdue(due.isOverdue())
                .daysOverdue(due.getDaysOverdue())
                .reminderEligible(due.isReminderEligible())
                .refundedAmount(refunded)
                .retainedAmount(retained)
                .netCollectedAmount(netCollected)
                .partiallyRefunded(partiallyRefunded)
                .financialDisposition(disposition)
                .build();
    }

    private static PaymentFinancialDisposition resolveDisposition(
            SpacePaymentEntity entity, BigDecimal refunded, BigDecimal retained) {
        if (entity.getPaymentStatus() == SpacePaymentStatus.CANCELLED) {
            return PaymentFinancialDisposition.CANCELLED_UNPAID;
        }
        if (entity.getPaymentStatus() == SpacePaymentStatus.REFUNDED) {
            return PaymentFinancialDisposition.FULLY_REFUNDED;
        }
        if (entity.getPaymentStatus() != SpacePaymentStatus.PAID) {
            return PaymentFinancialDisposition.OPEN;
        }
        BigDecimal amount = entity.getAmount() != null ? entity.getAmount() : BigDecimal.ZERO;
        if (refunded.signum() <= 0 && retained.signum() <= 0) {
            return PaymentFinancialDisposition.COLLECTED_UNSETTLED;
        }
        if (refunded.compareTo(amount) >= 0) {
            return PaymentFinancialDisposition.FULLY_REFUNDED;
        }
        if (retained.compareTo(amount) >= 0) {
            return PaymentFinancialDisposition.FULLY_RETAINED;
        }
        if (refunded.signum() > 0) {
            return PaymentFinancialDisposition.PARTIALLY_REFUNDED;
        }
        return PaymentFinancialDisposition.FULLY_RETAINED;
    }

    public static SpacePaymentResponse from(
            SpacePaymentEntity entity, List<LocalDate> mealDates, SpaceEntity space) {
        return from(entity, mealDates, PaymentDueStatusCalculator.businessDate(space));
    }

    /** Copy with an updated target label without dropping billing/due fields. */
    public SpacePaymentResponse withTargetLabel(String label) {
        return SpacePaymentResponse.builder()
                .paymentId(paymentId)
                .spaceId(spaceId)
                .memberId(memberId)
                .memberName(memberName)
                .memberMobile(memberMobile)
                .occupancyId(occupancyId)
                .stayKind(stayKind)
                .paymentType(paymentType)
                .paymentCategory(paymentCategory)
                .title(title)
                .description(description)
                .amount(amount)
                .currencyCode(currencyCode)
                .dueDate(dueDate)
                .month(month)
                .paymentMethod(paymentMethod)
                .paymentStatus(paymentStatus)
                .proofUrl(proofUrl)
                .proofFileId(proofFileId)
                .referenceNumber(referenceNumber)
                .remarks(remarks)
                .rejectionReason(rejectionReason)
                .rejectionCode(rejectionCode)
                .reviewedBy(reviewedBy)
                .reviewedAt(reviewedAt)
                .paymentDate(paymentDate)
                .targetLabel(label)
                .paymentBatchId(paymentBatchId)
                .paymentReference(paymentReference)
                .mealDates(mealDates)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .billingPeriodStart(billingPeriodStart)
                .billingPeriodEnd(billingPeriodEnd)
                .billableDays(billableDays)
                .daysInMonth(daysInMonth)
                .isProrated(isProrated)
                .configuredMonthlyAmount(configuredMonthlyAmount)
                .taxEnabled(taxEnabled)
                .taxRatePercent(taxRatePercent)
                .priceTaxMode(priceTaxMode)
                .baseAmount(baseAmount)
                .taxAmount(taxAmount)
                .paidAmount(paidAmount)
                .outstandingAmount(outstandingAmount)
                .settlementStatus(settlementStatus)
                .isOverdue(isOverdue)
                .daysOverdue(daysOverdue)
                .reminderEligible(reminderEligible)
                .refundedAmount(refundedAmount)
                .retainedAmount(retainedAmount)
                .netCollectedAmount(netCollectedAmount)
                .partiallyRefunded(partiallyRefunded)
                .financialDisposition(financialDisposition)
                .build();
    }
}
