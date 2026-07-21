package com.countin.countin_backend.payment.api.dto.response;

import com.countin.countin_backend.common.web.PagedResponse;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentsCardsPageResponse {

    private String month;
    private String queue;
    private PagedResponse<SpacePaymentResponse> page;
}
