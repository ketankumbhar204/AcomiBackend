package com.acomi.acomi_backend.enquiry.api.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminEnquirySummaryResponse {

    private long totalEnquiries;
    private long pendingCount;
    private long sharedCount;
    private long expiredCount;
    private long rejectedCount;
    private long cancelledCount;
}
