package com.acomi.acomi_backend.meal.api.dto.response;

import com.acomi.acomi_backend.meal.domain.model.MealPollPaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Meal activity summary for a single calendar day")
public class MemberMealActivityDayResponse {

    @Schema(description = "Calendar date", example = "2026-06-19")
    private LocalDate date;

    @Schema(description = "Whether the day can be opened for detail (any non-inactive meal slot)")
    private boolean hasActivity;

    private BigDecimal dayTotal;
    private String currencyCode;
    private MealPollPaymentStatus paymentStatus;
    private String paymentBatchId;
    /** Immutable human-readable payment reference when a payment exists for the day. */
    private String paymentReference;

    private List<MemberMealActivitySlotResponse> slots;
}
