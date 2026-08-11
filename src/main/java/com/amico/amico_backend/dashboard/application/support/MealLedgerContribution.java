package com.amico.amico_backend.dashboard.application.support;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealLedgerContribution {

    private BigDecimal expected;
    private BigDecimal collected;
    private boolean hasExpected;
    private boolean hasCollected;
    private String currencyCode;
}
