package com.acomi.acomi_backend.dashboard.api.dto.response;

import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GlobalAttentionSpaceResponse {

    private UUID spaceId;
    private String spaceName;
    private String spaceType;
    private int count;
    private List<GlobalAttentionItemResponse> items;
}
