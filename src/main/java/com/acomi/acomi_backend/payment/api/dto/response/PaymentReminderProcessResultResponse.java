package com.acomi.acomi_backend.payment.api.dto.response;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentReminderProcessResultResponse {

    private LocalDate businessDate;
    private boolean providerConfigured;
    private String providerMode;
    private int candidatesFound;
    private int remindersCreated;
    private int remindersSkippedDuplicate;
    private int deliverySuccesses;
    private int deliveryFailures;
    private List<PaymentReminderDeliveryResponse> deliveries;
}
