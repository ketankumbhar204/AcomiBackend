package com.countin.countin_backend.dashboard.api.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GlobalDashboardResponse {

    private int totalAttentionCount;
    private int unreadNotificationCount;
    private List<GlobalAttentionSpaceResponse> attentionRequired;
    private boolean attentionHasMore;
    private List<GlobalActivityItemResponse> recentActivity;
    private boolean activityHasMore;
    private List<GlobalSpaceStatusResponse> spaceSummaries;
}
