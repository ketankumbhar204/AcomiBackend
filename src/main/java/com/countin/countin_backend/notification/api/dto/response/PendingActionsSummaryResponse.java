package com.countin.countin_backend.notification.api.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PendingActionsSummaryResponse {
    int totalCount;
    List<PendingActionGroupResponse> groups;
}
