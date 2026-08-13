package com.acomi.acomi_backend.meal.api.dto.response;

import com.acomi.acomi_backend.meal.domain.model.MealPollPaymentChoice;
import com.acomi.acomi_backend.meal.domain.model.MealPollPaymentStatus;
import com.acomi.acomi_backend.meal.domain.model.MealType;
import com.acomi.acomi_backend.space.domain.model.MealBillingType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealPollDayResponse {

    private LocalDate pollDate;
    private List<MealPollResponse> polls;
    private MealPollPaymentStatus myPaymentStatus;
    private MealPollPaymentChoice myPaymentChoice;
    private String myProofImageUrl;
    private String myRejectionReason;
    private java.util.List<MealDeliveryLocationResponse> deliveryLocations;
    private Map<MealType, UUID> myLastDeliveryLocationIds;
    private MealBillingType myMealBillingType;
    private java.math.BigDecimal myPrepaidOverflowAmount;
    private java.math.BigDecimal myPrepaidDebitedAmount;
    private Boolean myPrepaidOverflowPayment;
    /** Persisted meal total for this member/day. */
    private BigDecimal myPaymentChargedAmount;
    /**
     * Ephemeral delta from this submit only (Paid meal edits). Not stored.
     * Positive = additional due on next bill; negative = credit.
     */
    private BigDecimal myPaymentAdjustment;
}
