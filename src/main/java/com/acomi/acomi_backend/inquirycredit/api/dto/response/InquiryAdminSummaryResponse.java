package com.acomi.acomi_backend.inquirycredit.api.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InquiryAdminSummaryResponse {

    private long pendingCount;
    private long approvedCount;
    private long rejectedCount;
}
