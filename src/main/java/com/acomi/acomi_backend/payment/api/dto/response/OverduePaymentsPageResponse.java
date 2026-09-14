package com.acomi.acomi_backend.payment.api.dto.response;

import com.acomi.acomi_backend.common.web.PagedResponse;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OverduePaymentsPageResponse {

    private LocalDate businessDate;
    private PagedResponse<OverduePaymentResponse> page;
}
