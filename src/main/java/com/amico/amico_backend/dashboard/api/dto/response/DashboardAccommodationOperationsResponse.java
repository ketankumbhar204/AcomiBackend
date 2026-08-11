package com.amico.amico_backend.dashboard.api.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardAccommodationOperationsResponse {

    private int occupiedBeds;
    private int vacantBeds;
    private int moveInsThisMonth;
    private int pendingPaymentsCount;
}
