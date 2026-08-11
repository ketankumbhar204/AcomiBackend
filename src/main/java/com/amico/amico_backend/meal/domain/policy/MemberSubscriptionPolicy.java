package com.amico.amico_backend.meal.domain.policy;

import com.amico.amico_backend.meal.api.dto.response.MemberMealBalanceResponse;
import com.amico.amico_backend.meal.domain.model.MealBalanceLedgerEntryType;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MemberMealBalanceEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MemberMealBalanceLedgerEntryEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.MemberMealBalanceLedgerRepository;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.MemberMealBalanceRepository;
import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.amico.amico_backend.space.domain.model.MealBillingType;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberSubscriptionPolicy {

    private final MemberMealBalanceRepository balanceRepository;
    private final MemberMealBalanceLedgerRepository ledgerRepository;

    public boolean isPrepaidBilling(SpaceEntity space, MemberEntity member) {
        MealBillingType billing = member.getMealBillingType() != null
                ? member.getMealBillingType()
                : space.getMealBillingType();
        return billing == MealBillingType.PREPAID_BALANCE;
    }

    public boolean hasActiveSubscription(UUID spaceId, UUID memberId) {
        Optional<MemberMealBalanceEntity> balanceOpt =
                balanceRepository.findBySpaceIdAndMemberId(spaceId, memberId);
        if (balanceOpt.isEmpty()) {
            return false;
        }
        MemberMealBalanceEntity balance = balanceOpt.get();
        if (balance.getSubscriptionEndedAt() != null) {
            return false;
        }
        MemberMealBalanceLedgerEntryEntity lastPurchase = balance.getId() != null
                ? ledgerRepository
                        .findFirstByBalanceIdAndEntryTypeOrderByCreatedAtDesc(
                                balance.getId(), MealBalanceLedgerEntryType.PURCHASE)
                        .orElse(null)
                : null;
        if (lastPurchase == null) {
            return false;
        }
        LocalDate validTill = resolveValidTill(lastPurchase);
        return validTill != null && !validTill.isBefore(LocalDate.now());
    }

    public String resolveLifecycleStatus(UUID spaceId, UUID memberId) {
        Optional<MemberMealBalanceEntity> balanceOpt =
                balanceRepository.findBySpaceIdAndMemberId(spaceId, memberId);
        if (balanceOpt.isEmpty()) {
            return "none";
        }
        MemberMealBalanceEntity balance = balanceOpt.get();
        if (balance.getSubscriptionEndedAt() != null) {
            return "ended";
        }
        MemberMealBalanceLedgerEntryEntity lastPurchase = balance.getId() != null
                ? ledgerRepository
                        .findFirstByBalanceIdAndEntryTypeOrderByCreatedAtDesc(
                                balance.getId(), MealBalanceLedgerEntryType.PURCHASE)
                        .orElse(null)
                : null;
        if (lastPurchase == null) {
            return "none";
        }
        LocalDate validTill = resolveValidTill(lastPurchase);
        if (validTill == null) {
            return "active";
        }
        if (validTill.isBefore(LocalDate.now())) {
            return "expired";
        }
        long daysLeft = validTill.toEpochDay() - LocalDate.now().toEpochDay();
        BigDecimal remaining = balance.getBalance();
        if (daysLeft <= 3 || (remaining != null && remaining.intValue() <= 3)) {
            return "expiring_soon";
        }
        return "active";
    }

    public boolean canParticipateInPolls(SpaceEntity space, MemberEntity member) {
        if (!isPrepaidBilling(space, member)) {
            return true;
        }
        return hasActiveSubscription(space.getId(), member.getId());
    }

    private static LocalDate resolveValidTill(MemberMealBalanceLedgerEntryEntity lastPurchase) {
        if (lastPurchase.getValidTill() != null) {
            return lastPurchase.getValidTill();
        }
        if (lastPurchase.getCreatedAt() == null) {
            return null;
        }
        return lastPurchase.getCreatedAt().toLocalDate().with(TemporalAdjusters.lastDayOfMonth());
    }
}
