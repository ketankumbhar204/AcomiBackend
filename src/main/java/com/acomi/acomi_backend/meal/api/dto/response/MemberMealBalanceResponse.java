package com.acomi.acomi_backend.meal.api.dto.response;

import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MemberMealBalanceEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MemberMealBalanceLedgerEntryEntity;
import com.acomi.acomi_backend.space.domain.model.PrepaidBalanceUnit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberMealBalanceResponse {

    private BigDecimal balance;
    private PrepaidBalanceUnit unit;
    private String currencyCode;
    private BigDecimal purchasedThisMonth;
    private BigDecimal consumedThisMonth;
    private BigDecimal amountPaidThisMonth;
    private BigDecimal lastPurchaseMeals;
    private BigDecimal lastPurchasePaidAmount;
    /** Cumulative amount collected across all meal additions in the active subscription. */
    private BigDecimal currentAmountPaid;
    private LocalDateTime lastPurchaseAt;
    /** Cumulative meals purchased across all additions in the active subscription. */
    private BigDecimal mealsIncluded;
    private BigDecimal mealsUsed;
    private BigDecimal mealsRemaining;
    private LocalDateTime validTill;
    private boolean active;
    private LocalDateTime endedAt;
    private UUID endedBy;

    public static MemberMealBalanceResponse of(
            MemberMealBalanceEntity balance,
            BigDecimal purchasedThisMonth,
            BigDecimal consumedThisMonth,
            BigDecimal amountPaidThisMonth,
            MemberMealBalanceLedgerEntryEntity lastPurchase,
            BigDecimal totalMealsPurchased,
            BigDecimal totalAmountPaid,
            BigDecimal mealsUsed) {
        BigDecimal mealsRemaining = balance.getBalance();
        LocalDate validTillDate = resolveValidTill(lastPurchase);
        LocalDateTime validTill = validTillDate != null ? validTillDate.atTime(23, 59, 59) : null;
        LocalDateTime endedAt = balance.getSubscriptionEndedAt();
        boolean manuallyEnded = endedAt != null;
        boolean active = !manuallyEnded
                && lastPurchase != null
                && validTillDate != null
                && !validTillDate.isBefore(LocalDate.now());

        return MemberMealBalanceResponse.builder()
                .balance(mealsRemaining)
                .unit(balance.getUnit())
                .currencyCode(balance.getCurrencyCode())
                .purchasedThisMonth(purchasedThisMonth)
                .consumedThisMonth(consumedThisMonth)
                .amountPaidThisMonth(amountPaidThisMonth)
                .lastPurchaseMeals(lastPurchase != null ? lastPurchase.getAmount() : null)
                .lastPurchasePaidAmount(lastPurchase != null ? lastPurchase.getPaidAmount() : null)
                .currentAmountPaid(totalAmountPaid)
                .lastPurchaseAt(lastPurchase != null ? lastPurchase.getCreatedAt() : null)
                .mealsIncluded(totalMealsPurchased)
                .mealsUsed(mealsUsed)
                .mealsRemaining(mealsRemaining)
                .validTill(validTill)
                .active(active)
                .endedAt(endedAt)
                .endedBy(balance.getSubscriptionEndedBy())
                .build();
    }

    private static LocalDate resolveValidTill(MemberMealBalanceLedgerEntryEntity lastPurchase) {
        if (lastPurchase == null) {
            return null;
        }
        if (lastPurchase.getValidTill() != null) {
            return lastPurchase.getValidTill();
        }
        if (lastPurchase.getCreatedAt() == null) {
            return null;
        }
        return lastPurchase.getCreatedAt().toLocalDate().with(TemporalAdjusters.lastDayOfMonth());
    }
}
