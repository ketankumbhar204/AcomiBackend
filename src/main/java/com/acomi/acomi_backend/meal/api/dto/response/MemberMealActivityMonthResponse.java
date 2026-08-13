package com.acomi.acomi_backend.meal.api.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberMealActivityMonthResponse {

    private String month;
    private MemberMealActivitySummaryResponse summary;
    private List<MemberMealActivityDayResponse> days;
}
