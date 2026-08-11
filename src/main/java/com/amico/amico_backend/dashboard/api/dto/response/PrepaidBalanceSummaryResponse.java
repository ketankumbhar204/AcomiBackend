package com.amico.amico_backend.dashboard.api.dto.response;

import com.amico.amico_backend.space.domain.model.PrepaidBalanceUnit;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PrepaidBalanceSummaryResponse {

    /** Total balance sold / topped up this month (meals or currency). */
    private BigDecimal balanceSold;

    /** Total balance consumed this month. */
    private BigDecimal balanceConsumed;

    /** Remaining balance across active members (snapshot). */
    private BigDecimal balanceRemaining;

    /** Money collected from balance sales this month. */
    private BigDecimal amountCollected;

    private PrepaidBalanceUnit unit;

    private String currencyCode;
}
