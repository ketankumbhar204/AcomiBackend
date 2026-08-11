package com.amico.amico_backend.payment.api.dto.response;

import com.amico.amico_backend.common.web.PagedResponse;
import com.amico.amico_backend.dashboard.api.dto.response.MemberPaymentLedgerRowResponse;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentsMembersPageResponse {

    private String month;
    private PagedResponse<MemberPaymentLedgerRowResponse> page;
}
