package com.acomi.acomi_backend.meal.api.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberMealActivitySubscriptionResponse {

    private String planName;
    private Integer creditsConsumed;
    private Integer creditsRemaining;
    private boolean coveredBySubscription;
}
