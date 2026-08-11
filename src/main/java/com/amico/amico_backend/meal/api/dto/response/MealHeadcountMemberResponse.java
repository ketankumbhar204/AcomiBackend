package com.amico.amico_backend.meal.api.dto.response;

import com.amico.amico_backend.meal.domain.model.MealPollPaymentStatus;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealHeadcountMemberResponse {

    private UUID memberId;
    private String memberName;
    private int quantity;
    private MealPollPaymentStatus paymentStatus;
    private String paymentProofImageUrl;
    private UUID deliveryLocationId;
    private String deliveryLocationName;
}