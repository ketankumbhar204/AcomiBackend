package com.acomi.acomi_backend.admin.api.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminUserRegistrationBreakdownSliceResponse {

    /** OWNER | MEMBER | OWNER_AND_MEMBER | NOT_SELECTED */
    private String role;
    private String label;
    private long count;
}
