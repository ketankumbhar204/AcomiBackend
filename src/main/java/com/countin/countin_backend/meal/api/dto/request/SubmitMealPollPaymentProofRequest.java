package com.countin.countin_backend.meal.api.dto.request;

import com.countin.countin_backend.payment.domain.model.SpacePaymentMethod;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmitMealPollPaymentProofRequest {

    private String proofImageBase64;

    @Size(max = 100)
    private String referenceNumber;

    @Size(max = 2000)
    private String remarks;

    private SpacePaymentMethod paymentMethod;
}
