package com.acomi.acomi_backend.payment.api.dto.response;

import com.acomi.acomi_backend.payment.application.support.PaymentDueStatusCalculator;
import com.acomi.acomi_backend.payment.application.support.PaymentDueStatusCalculator.DueStatus;
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
    private UUID occupancyId;
    private SpacePaymentType paymentType;
    private SpacePaymentCategory paymentCategory;
    private String title;
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
        return SpacePaymentResponse.builder()
                .paymentId(entity.getId())
                .spaceId(entity.getSpace().getId())
                .memberId(entity.getMember().getId())
                .memberName(entity.getMember().getFullName())
                .occupancyId(entity.getOccupancy() != null ? entity.getOccupancy().getId() : null)
                .paymentType(entity.getPaymentType())
                .paymentCategory(entity.getPaymentCategory())
                .title(entity.getTitle())
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
                .build();
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
                .occupancyId(occupancyId)
                .paymentType(paymentType)
                .paymentCategory(paymentCategory)
                .title(title)
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
                .build();
    }
}
