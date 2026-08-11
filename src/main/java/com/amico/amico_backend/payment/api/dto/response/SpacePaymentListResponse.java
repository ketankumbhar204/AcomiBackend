package com.amico.amico_backend.payment.api.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SpacePaymentListResponse {

    private String month;
    private List<SpacePaymentResponse> payments;
}
