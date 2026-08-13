package com.acomi.acomi_backend.meal.api.dto.response;

import com.acomi.acomi_backend.meal.domain.model.SubscriptionActivationRequestStatus;
import com.acomi.acomi_backend.space.domain.model.MealBillingType;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CustomerSubscriptionStatusResponse {

    private MealBillingType mealBillingType;
    private boolean prepaidBilling;
    private boolean subscriptionActive;
    private String lifecycleStatus;
    private LocalDateTime validTill;
    private LocalDateTime endedAt;
    private Integer mealsRemaining;
    private SubscriptionActivationRequestStatus pendingActivationStatus;
    private UUID pendingActivationRequestId;
    private String pendingPlanName;
}
