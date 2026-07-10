package com.countin.countin_backend.dashboard.api.dto.response;

import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GlobalSpaceStatusResponse {

    private UUID spaceId;
    private String spaceName;
    private String spaceType;
    private String membershipRole;
    private int pendingActionCount;
    private boolean needsAttention;
}
