package com.countin.countin_backend.meal.api.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MenuHistoryPageResponse {

    private List<MenuHistoryItemResponse> items;
    private int page;
    private int limit;
    private long total;
    private boolean hasMore;
}
