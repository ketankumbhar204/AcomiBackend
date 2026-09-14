package com.acomi.acomi_backend.admin.api.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminMessRegistrationsSummaryResponse {

    private long totalMess;
    private long leads;
    private long activeMess;
    private long registeredByVendors;
    private Double totalMessDeltaPercent;
    private Double leadsDeltaPercent;
    private Double activeMessDeltaPercent;
    private Double registeredByVendorsDeltaPercent;
}
