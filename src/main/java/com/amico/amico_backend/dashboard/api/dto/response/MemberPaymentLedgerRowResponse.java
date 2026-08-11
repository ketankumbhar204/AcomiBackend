package com.amico.amico_backend.dashboard.api.dto.response;

import com.amico.amico_backend.dashboard.domain.model.MemberPaymentStatus;
import com.amico.amico_backend.space.domain.model.MealBillingType;
import com.amico.amico_backend.space.domain.model.PrepaidBalanceUnit;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberPaymentLedgerRowResponse {

    private UUID memberId;
    private String memberName;
    private BigDecimal expectedCharges;
    private BigDecimal collected;
    /** Submitted proofs awaiting owner review. */
    private BigDecimal underReview;
    private BigDecimal pending;
    private String currencyCode;
    private MemberPaymentStatus status;
    private MealBillingType mealBillingType;
    private BigDecimal mealBalanceRemaining;
    private BigDecimal mealBalancePurchased;
    private BigDecimal mealBalanceConsumed;
    private PrepaidBalanceUnit mealBalanceUnit;
}
