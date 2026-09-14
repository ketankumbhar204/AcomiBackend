package com.acomi.acomi_backend.admin.api.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminPropertyRegistrationsSummaryResponse {

    private long totalProperties;
    private long leads;
    private long activeProperties;
    private long registeredByOwners;
    private Double totalPropertiesDeltaPercent;
    private Double leadsDeltaPercent;
    private Double activePropertiesDeltaPercent;
    private Double registeredByOwnersDeltaPercent;
}
