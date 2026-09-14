package com.acomi.acomi_backend.admin.api.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminRegisteredUsersSummaryResponse {

    private long totalUsers;
    private long verifiedUsers;
    private long newUsersLast30Days;
    private long withSpaceAssociation;
    private Double totalUsersDeltaPercent;
    private Double verifiedUsersDeltaPercent;
    private Double newUsersDeltaPercent;
    private Double withSpaceDeltaPercent;
}
