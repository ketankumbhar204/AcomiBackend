package com.acomi.acomi_backend.dashboard.api.dto.response;

import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberPaymentLedgerResponse {

    private String month;
    private SpaceType spaceType;
    private DashboardFinancialSummaryResponse summary;
    private List<MemberPaymentLedgerRowResponse> members;
}
