package com.countin.countin_backend.payment.api.dto.response;

import com.countin.countin_backend.payment.domain.model.PaymentRejectionReason;
import com.countin.countin_backend.payment.domain.model.SpacePaymentCategory;
import com.countin.countin_backend.payment.domain.model.SpacePaymentMethod;
import com.countin.countin_backend.payment.domain.model.SpacePaymentStatus;
import com.countin.countin_backend.payment.domain.model.SpacePaymentType;
import com.countin.countin_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
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
    private String referenceNumber;
    private String remarks;
    private String rejectionReason;
    private PaymentRejectionReason rejectionCode;
    private UUID reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDate paymentDate;
    private String targetLabel;
    /** Present when this payment covers multiple meal days from one bulk proof. */
    private String paymentBatchId;
    /**
     * Immutable human-readable payment reference (e.g. PAY-20260720-000123).
     * Prefer this over paymentBatchId / paymentId for customer and owner display.
     */
    private String paymentReference;
    /** Meal day dates covered by this payment (what was paid). */
    private List<LocalDate> mealDates;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SpacePaymentResponse from(SpacePaymentEntity entity) {
        return from(entity, null);
    }

    public static SpacePaymentResponse from(SpacePaymentEntity entity, List<LocalDate> mealDates) {
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
                .proofUrl(entity.getProofUrl())
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
                .build();
    }
}
