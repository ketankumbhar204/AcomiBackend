package com.countin.countin_backend.payment.api.dto.request;

import com.countin.countin_backend.payment.domain.model.SpacePaymentMethod;
import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
public class SubmitSpacePaymentProofRequest {

    private String proofImageBase64;

    private String referenceNumber;
    private String remarks;
    private SpacePaymentMethod paymentMethod;
}
