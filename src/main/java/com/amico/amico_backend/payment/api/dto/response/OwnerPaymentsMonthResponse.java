package com.amico.amico_backend.payment.api.dto.response;

import com.amico.amico_backend.dashboard.api.dto.response.DashboardFinancialSummaryResponse;
import com.amico.amico_backend.dashboard.api.dto.response.MemberPaymentLedgerRowResponse;
import com.amico.amico_backend.space.domain.model.SpaceType;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * Aggregated owner Payments screen payload — summary, members, payment rows, and counts
 * computed once per request.
 */
@Getter
@Builder
public class OwnerPaymentsMonthResponse {

    private String month;
    private SpaceType spaceType;
    private DashboardFinancialSummaryResponse summary;
    private List<MemberPaymentLedgerRowResponse> members;
    private List<SpacePaymentResponse> payments;
    private OwnerPaymentsMonthCountsResponse counts;
}
