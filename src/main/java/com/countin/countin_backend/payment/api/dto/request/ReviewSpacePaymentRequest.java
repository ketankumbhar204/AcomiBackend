package com.countin.countin_backend.payment.api.dto.request;

import com.countin.countin_backend.payment.domain.model.PaymentRejectionReason;
import com.countin.countin_backend.payment.domain.model.PaymentReviewAction;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewSpacePaymentRequest {

    @NotNull
    private PaymentReviewAction action;

    private String remarks;
    private PaymentRejectionReason rejectionCode;
}
