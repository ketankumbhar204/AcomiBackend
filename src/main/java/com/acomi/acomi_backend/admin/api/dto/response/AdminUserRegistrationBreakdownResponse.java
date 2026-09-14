package com.acomi.acomi_backend.admin.api.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminUserRegistrationBreakdownResponse {

    private long total;
    private List<AdminUserRegistrationBreakdownSliceResponse> slices;
}
