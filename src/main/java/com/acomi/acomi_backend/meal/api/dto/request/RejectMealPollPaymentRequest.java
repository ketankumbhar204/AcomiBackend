package com.acomi.acomi_backend.meal.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RejectMealPollPaymentRequest {

    private String rejectionReason;
}
