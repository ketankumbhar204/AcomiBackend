package com.countin.countin_backend.payment.api.dto.response;

import com.countin.countin_backend.common.web.PagedResponse;
import com.countin.countin_backend.dashboard.api.dto.response.MemberPaymentLedgerRowResponse;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentsMembersPageResponse {

    private String month;
    private PagedResponse<MemberPaymentLedgerRowResponse> page;
}
