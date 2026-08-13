package com.acomi.acomi_backend.payment.api.dto.response;

import com.acomi.acomi_backend.common.web.PagedResponse;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentsCardsPageResponse {

    private String month;
    private String queue;
    private PagedResponse<SpacePaymentResponse> page;
}
