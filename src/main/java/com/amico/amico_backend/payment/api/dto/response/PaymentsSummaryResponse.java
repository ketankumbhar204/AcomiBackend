package com.amico.amico_backend.payment.api.dto.response;

import com.amico.amico_backend.dashboard.api.dto.response.DashboardFinancialSummaryResponse;
import com.amico.amico_backend.space.domain.model.SpaceType;
import lombok.Builder;
import lombok.Getter;

/** Lightweight Payments KPI + tab counts. Never includes member or payment lists. */
@Getter
@Builder
public class PaymentsSummaryResponse {

    private String month;
    private SpaceType spaceType;
    private DashboardFinancialSummaryResponse financial;
    private OwnerPaymentsMonthCountsResponse counts;
}
