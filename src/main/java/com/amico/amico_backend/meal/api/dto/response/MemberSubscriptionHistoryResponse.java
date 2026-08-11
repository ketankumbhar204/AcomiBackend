package com.amico.amico_backend.meal.api.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberSubscriptionHistoryResponse {

    private MemberSubscriptionLifetimeSummaryResponse summary;
    private List<MemberMealBalanceActivityEventResponse> events;
}
