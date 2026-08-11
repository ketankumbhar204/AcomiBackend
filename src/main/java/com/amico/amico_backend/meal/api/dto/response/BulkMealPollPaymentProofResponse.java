package com.amico.amico_backend.meal.api.dto.response;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BulkMealPollPaymentProofResponse {

    private String paymentBatchId;
    /** Immutable human-readable payment reference for the submitted batch. */
    private String paymentReference;
    private List<LocalDate> dates;
    private int updatedCount;
}
