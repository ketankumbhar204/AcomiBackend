package com.acomi.acomi_backend.meal.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberMealActivityDayDetailResponse {

    private LocalDate date;
    private String memberName;
    private boolean hasActivity;
    private LocalDateTime responseSubmittedAt;
    private BigDecimal dayTotal;
    private String currencyCode;
    private MemberMealActivityDayPaymentResponse payment;
    private MemberMealActivitySubscriptionResponse subscription;
    private String notes;
    private List<MemberMealActivityDailyChargeResponse> dailyCharges;
    private List<MemberMealActivitySlotDetailResponse> slots;
}
