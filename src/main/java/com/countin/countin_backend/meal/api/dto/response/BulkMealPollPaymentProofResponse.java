package com.countin.countin_backend.meal.api.dto.response;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BulkMealPollPaymentProofResponse {

    private String paymentBatchId;
    private List<LocalDate> dates;
    private int updatedCount;
}
