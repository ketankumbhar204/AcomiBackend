package com.amico.amico_backend.dashboard.api.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardMessOperationsResponse {

    private int membersReceivingMeals;
    private int menusPublishedThisMonth;
    private int openPollsCount;
    private Integer todaysHeadcount;
    private int pollRespondedCount;
    private int pollEligibleCount;
}
