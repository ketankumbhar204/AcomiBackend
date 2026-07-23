package com.countin.countin_backend.meal.api.dto.response;

import com.countin.countin_backend.meal.domain.model.MealPollPaymentChoice;
import com.countin.countin_backend.meal.domain.model.MealPollPaymentStatus;
import com.countin.countin_backend.payment.domain.model.SpacePaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberMealActivityDayPaymentResponse {

    private UUID id;
    private LocalDate pollDate;
    private MealPollPaymentChoice paymentChoice;
    private MealPollPaymentStatus paymentStatus;
    private BigDecimal chargedAmount;
    private String paymentBatchId;
    /** Immutable human-readable payment reference (e.g. PAY-20260720-000123). */
    private String paymentReference;
    private String proofImageUrl;
    private String referenceNumber;
    private String remarks;
    private SpacePaymentMethod paymentMethod;
    private String rejectionReason;
    private LocalDateTime proofSubmittedAt;
    private LocalDateTime proofReviewedAt;
    private BigDecimal prepaidOverflowAmount;
    private BigDecimal prepaidDebitedAmount;
    private boolean prepaidOverflowPayment;
}
