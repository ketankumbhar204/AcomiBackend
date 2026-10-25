package com.acomi.acomi_backend.enquiry.api.dto.response;

import lombok.Builder;
import lombok.Getter;

/** Counts for listings the caller owns or manages. Not filtered by search text. */
@Getter
@Builder
public class ReceivedEnquirySummaryResponse {
    private long total;
    private long pending;
    private long shared;
    private long closed;
}
