package com.countin.countin_backend.payment.api.dto.request;

import com.countin.countin_backend.payment.domain.model.PaymentRejectionReason;
import com.countin.countin_backend.payment.domain.model.PaymentReviewAction;
import com.countin.countin_backend.payment.domain.model.SpacePaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmitSpacePaymentProofRequest {

    @NotBlank
    private String proofImageBase64;

    private String referenceNumber;
    private String remarks;
    private SpacePaymentMethod paymentMethod;
}
