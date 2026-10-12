package com.acomi.acomi_backend.member.application.support;

import java.math.BigDecimal;
import lombok.Getter;

/** Deposit figures shown on member details. Balance is money held: paid minus refunded. */
@Getter
public final class MemberDepositSummary {

    private final BigDecimal amount;
    private final BigDecimal paid;
    private final BigDecimal refunded;
    private final BigDecimal balance;

    public MemberDepositSummary(
            BigDecimal amount, BigDecimal paid, BigDecimal refunded, BigDecimal balance) {
        this.amount = amount;
        this.paid = paid;
        this.refunded = refunded;
        this.balance = balance;
    }
}
