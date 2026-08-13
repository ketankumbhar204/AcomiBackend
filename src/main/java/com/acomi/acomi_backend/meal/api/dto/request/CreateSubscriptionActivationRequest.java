package com.acomi.acomi_backend.meal.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateSubscriptionActivationRequest {

    @NotNull
    private UUID planId;

    private String paymentReference;

    private String proofImageBase64;

    private String customerNotes;
}
