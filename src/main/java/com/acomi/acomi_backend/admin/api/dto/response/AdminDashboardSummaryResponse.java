package com.acomi.acomi_backend.admin.api.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminDashboardSummaryResponse {

    private long propertyRegistrationCount;
    private long messRegistrationCount;
    private long adminPropertyLeads;
    private long adminMessLeads;
    private long websitePropertyLeads;
    private long websiteMessLeads;
    private long unclaimedAdminPropertyLeads;
    private long unclaimedAdminMessLeads;
    private long claimedPropertyLeads;
    private long claimedMessLeads;
    private long activePropertySpaces;
    private long activeMessSpaces;
    /** Phone-verified app users (unique users, not properties/messes). */
    private long registeredUsersCount;
}
