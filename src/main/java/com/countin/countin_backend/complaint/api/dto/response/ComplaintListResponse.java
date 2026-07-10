package com.countin.countin_backend.complaint.api.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ComplaintListResponse {

    private long totalCount;
    private long openCount;
    private long inProgressCount;
    private long resolvedCount;
    private List<ComplaintResponse> complaints;
}
