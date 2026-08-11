package com.amico.amico_backend.dashboard.api.dto.response;

import com.amico.amico_backend.space.domain.model.SpaceType;
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
