package com.acomi.acomi_backend.payment.api.dto.response;

import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.dashboard.api.dto.response.MemberPaymentLedgerRowResponse;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentsMembersPageResponse {

    private String month;
    private PagedResponse<MemberPaymentLedgerRowResponse> page;
}
