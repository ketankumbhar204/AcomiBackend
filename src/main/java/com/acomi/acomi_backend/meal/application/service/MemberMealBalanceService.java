package com.acomi.acomi_backend.meal.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.meal.api.dto.request.RecordMealBalancePurchaseRequest;
import com.acomi.acomi_backend.meal.api.dto.response.MemberMealBalanceActivityEventResponse;
import com.acomi.acomi_backend.meal.api.dto.response.MemberMealBalanceResponse;
import com.acomi.acomi_backend.meal.api.dto.response.MemberSubscriptionHistoryResponse;
import com.acomi.acomi_backend.meal.api.dto.response.MemberSubscriptionLifetimeSummaryResponse;
import com.acomi.acomi_backend.meal.application.support.MealPollCharge;
import com.acomi.acomi_backend.meal.application.support.MealPollChargeCalculator;
import com.acomi.acomi_backend.meal.application.support.MealBillingResolver;
import com.acomi.acomi_backend.meal.domain.model.MealBalanceLedgerEntryType;
import com.acomi.acomi_backend.meal.domain.model.MealSubscriptionAction;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MemberMealBalanceEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MemberMealBalanceLedgerEntryEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealPollEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealPollResponseEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MemberMealBalanceLedgerRepository;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MemberMealBalanceRepository;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MealPollResponseRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.acomi.acomi_backend.space.domain.model.MealBillingType;
import com.acomi.acomi_backend.space.domain.model.PrepaidBalanceUnit;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberMealBalanceService {

    private static final String DEFAULT_CURRENCY = "INR";

    private final MemberMealBalanceRepository balanceRepository;
    private final MemberMealBalanceLedgerRepository ledgerRepository;
    private final MealPollResponseRepository responseRepository;
    private final SpaceRepository spaceRepository;
    private final MemberRepository memberRepository;
    private final MealAccessService mealAccessService;
    private final MealBillingResolver mealBillingResolver;

    @Transactional(readOnly = true)
    public MemberMealBalanceResponse getBalance(UUID spaceId, UUID memberId, UUID callerId) {
        mealAccessService.requireViewMeals(spaceId, callerId);
        validateMember(spaceId, memberId);
        YearMonth month = YearMonth.now();
        MemberMealBalanceEntity balance = balanceRepository
                .findBySpaceIdAndMemberId(spaceId, memberId)
                .orElseGet(() -> emptyBalance(spaceId, memberId));
        return buildResponse(spaceId, memberId, month, balance);
    }

    @Transactional(readOnly = true)
    public List<MemberMealBalanceActivityEventResponse> getActivity(
            UUID spaceId, UUID memberId, UUID callerId, YearMonth month) {
        mealAccessService.requireViewMeals(spaceId, callerId);
        validateMember(spaceId, memberId);

        MemberMealBalanceEntity balance = balanceRepository
                .findBySpaceIdAndMemberId(spaceId, memberId)
                .orElse(null);
        if (balance == null) {
            return List.of();
        }

        List<MemberMealBalanceLedgerEntryEntity> entries = ledgerRepository
                .findByMemberAndCreatedAtBetweenOrderByCreatedAtAsc(
                        spaceId, memberId, monthStart(month), monthEndExclusive(month));
        if (entries.isEmpty()) {
            return List.of();
        }

        BigDecimal purchased = sumPurchased(spaceId, memberId, month);
        BigDecimal consumed = sumConsumed(spaceId, memberId, month);
        BigDecimal running = balance.getBalance().subtract(purchased).add(consumed);

        List<MemberMealBalanceActivityEventResponse> events = new ArrayList<>();
        for (MemberMealBalanceLedgerEntryEntity entry : entries) {
            if (entry.getEntryType() == MealBalanceLedgerEntryType.PURCHASE) {
                running = running.add(entry.getAmount());
            } else if (entry.getEntryType() == MealBalanceLedgerEntryType.DEBIT) {
                running = running.subtract(entry.getAmount());
            } else if (entry.getEntryType() == MealBalanceLedgerEntryType.ENDED) {
                running = BigDecimal.ZERO;
            }
            events.add(MemberMealBalanceActivityEventResponse.builder()
                    .eventId(entry.getId())
                    .eventType(entry.getEntryType())
                    .meals(entry.getAmount())
                    .paidAmount(entry.getPaidAmount())
                    .mealType(entry.getMealType())
                    .pollDate(entry.getPollDate())
                    .remarks(entry.getRemarks())
                    .balanceAfter(running)
                    .createdAt(entry.getCreatedAt())
                    .subscriptionAction(resolveSubscriptionAction(balance.getId(), entry))
                    .build());
        }
        Collections.reverse(events);
        return events;
    }

    @Transactional
    public MemberMealBalanceResponse recordPurchase(
            UUID spaceId, UUID memberId, UUID callerId, RecordMealBalancePurchaseRequest request) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        SpaceEntity space = loadSpace(spaceId);
        MemberEntity member = validateMember(spaceId, memberId);
        requirePrepaidBilling(space, member);

        MemberMealBalanceEntity balance = getOrCreateBalance(space, member);
        BigDecimal amount = request.getAmount();
        PrepaidBalanceUnit unit = resolveUnit(space);
        BigDecimal paidAmount = resolvePaidAmount(request, unit, amount);
        // Model B: every purchase adds to the accumulating meal balance.
        if (Boolean.TRUE.equals(request.getReplaceBalance())) {
            balance.setBalance(amount);
        } else {
            balance.setBalance(balance.getBalance().add(amount));
        }
        balance.setSubscriptionEndedAt(null);
        balance.setSubscriptionEndedBy(null);
        balanceRepository.save(balance);

        LocalDate validTill = resolvePurchaseValidTill(request);
        MemberMealBalanceLedgerEntryEntity.MemberMealBalanceLedgerEntryEntityBuilder ledgerBuilder =
                MemberMealBalanceLedgerEntryEntity.builder()
                        .balance(balance)
                        .entryType(MealBalanceLedgerEntryType.PURCHASE)
                        .amount(amount)
                        .requestedAmount(amount)
                        .paidAmount(paidAmount)
                        .validTill(validTill)
                        .idempotencyKey("PURCHASE:" + UUID.randomUUID())
                        .remarks(trimToNull(request.getRemarks()))
                        .createdBy(callerId);
        if (unit == PrepaidBalanceUnit.MEALS) {
            ledgerBuilder.mealCount(amount.intValue());
        }
        ledgerRepository.save(ledgerBuilder.build());

        YearMonth month = YearMonth.now();
        return buildResponse(spaceId, memberId, month, balance);
    }

    @Transactional
    public MemberMealBalanceResponse endSubscription(UUID spaceId, UUID memberId, UUID callerId) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        SpaceEntity space = loadSpace(spaceId);
        MemberEntity member = validateMember(spaceId, memberId);
        requirePrepaidBilling(space, member);

        MemberMealBalanceEntity balance = balanceRepository
                .findBySpaceIdAndMemberId(spaceId, memberId)
                .orElseThrow(() -> new BusinessException("No subscription to end", HttpStatus.BAD_REQUEST));

        if (balance.getSubscriptionEndedAt() != null) {
            throw new BusinessException("Subscription is already ended", HttpStatus.BAD_REQUEST);
        }

        MemberMealBalanceLedgerEntryEntity lastPurchase = ledgerRepository
                .findFirstByBalanceIdAndEntryTypeOrderByCreatedAtDesc(
                        balance.getId(), MealBalanceLedgerEntryType.PURCHASE)
                .orElseThrow(() -> new BusinessException("No active subscription to end", HttpStatus.BAD_REQUEST));

        balance.setBalance(BigDecimal.ZERO);
        balance.setSubscriptionEndedAt(LocalDateTime.now());
        balance.setSubscriptionEndedBy(callerId);
        balanceRepository.save(balance);

        ledgerRepository.save(MemberMealBalanceLedgerEntryEntity.builder()
                .balance(balance)
                .entryType(MealBalanceLedgerEntryType.ENDED)
                .amount(lastPurchase.getAmount())
                .requestedAmount(lastPurchase.getAmount())
                .paidAmount(lastPurchase.getPaidAmount())
                .mealCount(lastPurchase.getMealCount())
                .validTill(resolveLedgerValidTill(lastPurchase))
                .idempotencyKey("ENDED:" + UUID.randomUUID())
                .createdBy(callerId)
                .build());

        YearMonth month = YearMonth.now();
        return buildResponse(spaceId, memberId, month, balance);
    }

    @Transactional(readOnly = true)
    public MemberSubscriptionHistoryResponse getSubscriptionHistory(
            UUID spaceId, UUID memberId, UUID callerId) {
        mealAccessService.requireViewMeals(spaceId, callerId);
        validateMember(spaceId, memberId);

        MemberMealBalanceEntity balance = balanceRepository
                .findBySpaceIdAndMemberId(spaceId, memberId)
                .orElse(null);

        List<MemberMealBalanceLedgerEntryEntity> entries = ledgerRepository.findSubscriptionEventsByMemberChronological(
                spaceId,
                memberId,
                List.of(MealBalanceLedgerEntryType.PURCHASE, MealBalanceLedgerEntryType.ENDED));

        BigDecimal running = BigDecimal.ZERO;
        List<MemberMealBalanceActivityEventResponse> chronological = new ArrayList<>();
        for (MemberMealBalanceLedgerEntryEntity entry : entries) {
            if (entry.getEntryType() == MealBalanceLedgerEntryType.PURCHASE) {
                running = running.add(entry.getAmount());
            } else if (entry.getEntryType() == MealBalanceLedgerEntryType.ENDED) {
                running = BigDecimal.ZERO;
            }
            chronological.add(MemberMealBalanceActivityEventResponse.builder()
                    .eventId(entry.getId())
                    .eventType(entry.getEntryType())
                    .meals(entry.getAmount())
                    .paidAmount(entry.getPaidAmount())
                    .remarks(entry.getRemarks())
                    .balanceAfter(running)
                    .createdAt(entry.getCreatedAt())
                    .subscriptionAction(resolveSubscriptionAction(entry))
                    .build());
        }
        Collections.reverse(chronological);

        UUID balanceId = balance != null ? balance.getId() : null;
        MemberSubscriptionLifetimeSummaryResponse summary = MemberSubscriptionLifetimeSummaryResponse.builder()
                .totalMealsPurchased(sumTotalMealsPurchased(balanceId, null))
                .totalMealsConsumed(sumDebitsInPeriod(balanceId, null))
                .totalAmountPaid(sumTotalAmountPaid(balanceId, null))
                .totalActivities(chronological.size())
                .build();

        return MemberSubscriptionHistoryResponse.builder()
                .summary(summary)
                .events(chronological)
                .build();
    }

    private MemberMealBalanceResponse buildResponse(
            UUID spaceId, UUID memberId, YearMonth month, MemberMealBalanceEntity balance) {
        MemberMealBalanceLedgerEntryEntity lastPurchase = balance.getId() != null
                ? ledgerRepository
                        .findFirstByBalanceIdAndEntryTypeOrderByCreatedAtDesc(
                                balance.getId(), MealBalanceLedgerEntryType.PURCHASE)
                        .orElse(null)
                : null;
        LocalDateTime periodStart = resolveActiveSubscriptionPeriodStart(balance.getId());
        boolean closedAfterEnd = hasEndedEvent(balance.getId()) && periodStart == null;
        BigDecimal totalMealsPurchased = closedAfterEnd
                ? BigDecimal.ZERO
                : sumTotalMealsPurchased(balance.getId(), periodStart);
        BigDecimal totalAmountPaid = closedAfterEnd
                ? BigDecimal.ZERO
                : sumTotalAmountPaid(balance.getId(), periodStart);
        BigDecimal mealsRemaining = balance.getBalance();
        BigDecimal mealsUsedInPeriod = closedAfterEnd
                ? BigDecimal.ZERO
                : totalMealsPurchased.subtract(mealsRemaining != null ? mealsRemaining : BigDecimal.ZERO)
                        .max(BigDecimal.ZERO);
        return MemberMealBalanceResponse.of(
                balance,
                sumPurchased(spaceId, memberId, month),
                sumConsumed(spaceId, memberId, month),
                sumPaid(spaceId, memberId, month),
                lastPurchase,
                totalMealsPurchased,
                totalAmountPaid,
                mealsUsedInPeriod);
    }

    /**
     * Active subscription period starts at the first purchase after the most recent ENDED event.
     * When the subscription has never been ended, {@code null} means all purchases belong to the
     * current open subscription.
     */
    private LocalDateTime resolveActiveSubscriptionPeriodStart(UUID balanceId) {
        if (balanceId == null) {
            return null;
        }
        Optional<MemberMealBalanceLedgerEntryEntity> lastEnded = ledgerRepository
                .findFirstByBalanceIdAndEntryTypeOrderByCreatedAtDesc(
                        balanceId, MealBalanceLedgerEntryType.ENDED);
        if (lastEnded.isEmpty()) {
            return null;
        }
        return ledgerRepository
                .findFirstByBalanceIdAndEntryTypeAndCreatedAtAfterOrderByCreatedAtAsc(
                        balanceId, MealBalanceLedgerEntryType.PURCHASE, lastEnded.get().getCreatedAt())
                .map(MemberMealBalanceLedgerEntryEntity::getCreatedAt)
                .orElse(null);
    }

    private boolean hasEndedEvent(UUID balanceId) {
        if (balanceId == null) {
            return false;
        }
        return ledgerRepository
                .findFirstByBalanceIdAndEntryTypeOrderByCreatedAtDesc(
                        balanceId, MealBalanceLedgerEntryType.ENDED)
                .isPresent();
    }

    private BigDecimal sumTotalMealsPurchased(UUID balanceId, LocalDateTime since) {
        if (balanceId == null) {
            return BigDecimal.ZERO;
        }
        return ledgerRepository.sumAmountByBalanceIdAndEntryTypeSince(
                balanceId, MealBalanceLedgerEntryType.PURCHASE, since);
    }

    private BigDecimal sumTotalAmountPaid(UUID balanceId, LocalDateTime since) {
        if (balanceId == null) {
            return BigDecimal.ZERO;
        }
        return ledgerRepository.sumPaidAmountByBalanceIdAndEntryTypeSince(
                balanceId, MealBalanceLedgerEntryType.PURCHASE, since);
    }

    private BigDecimal sumDebitsInPeriod(UUID balanceId, LocalDateTime since) {
        if (balanceId == null) {
            return BigDecimal.ZERO;
        }
        if (since == null) {
            return ledgerRepository.sumAmountByBalanceIdAndEntryType(
                    balanceId, MealBalanceLedgerEntryType.DEBIT);
        }
        return ledgerRepository.sumAmountByBalanceIdAndEntryTypeSince(
                balanceId, MealBalanceLedgerEntryType.DEBIT, since);
    }

    private BigDecimal computeMealsUsed(BigDecimal totalPurchased, BigDecimal remaining) {
        if (totalPurchased == null) {
            return BigDecimal.ZERO;
        }
        return totalPurchased.subtract(remaining != null ? remaining : BigDecimal.ZERO).max(BigDecimal.ZERO);
    }

    private MealSubscriptionAction resolveSubscriptionAction(
            UUID balanceId, MemberMealBalanceLedgerEntryEntity entry) {
        return resolveSubscriptionAction(entry);
    }

    private MealSubscriptionAction resolveSubscriptionAction(MemberMealBalanceLedgerEntryEntity entry) {
        if (entry.getEntryType() == MealBalanceLedgerEntryType.ENDED) {
            return MealSubscriptionAction.ENDED;
        }
        if (entry.getEntryType() != MealBalanceLedgerEntryType.PURCHASE) {
            return null;
        }
        UUID balanceId = entry.getBalance().getId();
        long priorPurchases = ledgerRepository.countByBalanceIdAndEntryTypeAndCreatedAtBefore(
                balanceId, MealBalanceLedgerEntryType.PURCHASE, entry.getCreatedAt());
        if (priorPurchases == 0) {
            return MealSubscriptionAction.CREATED;
        }
        return MealSubscriptionAction.MEALS_ADDED;
    }

    private LocalDate resolvePurchaseValidTill(RecordMealBalancePurchaseRequest request) {
        if (request.getValidTill() != null) {
            return request.getValidTill();
        }
        return LocalDate.now().with(TemporalAdjusters.lastDayOfMonth());
    }

    private LocalDate resolveLedgerValidTill(MemberMealBalanceLedgerEntryEntity purchase) {
        if (purchase.getValidTill() != null) {
            return purchase.getValidTill();
        }
        if (purchase.getCreatedAt() == null) {
            return null;
        }
        return purchase.getCreatedAt().toLocalDate().with(TemporalAdjusters.lastDayOfMonth());
    }

    @Transactional
    public BigDecimal syncPollDebit(SpaceEntity space, MemberEntity member, MealPollEntity poll) {
        if (!mealBillingResolver.isPrepaid(space, member)) {
            return BigDecimal.ZERO;
        }

        List<MealPollResponseEntity> responses =
                responseRepository.findAllByPollIdAndMemberId(poll.getId(), member.getId());
        MealPollCharge charge = MealPollChargeCalculator.fromResponses(responses);
        PrepaidBalanceUnit unit = resolveUnit(space);
        BigDecimal requested = charge.requestedDebitAmount(unit);

        String idempotencyKey = debitKey(poll.getId(), member.getId());
        Optional<MemberMealBalanceLedgerEntryEntity> existing =
                ledgerRepository.findByIdempotencyKey(idempotencyKey);
        MemberMealBalanceEntity balance = getOrCreateBalance(space, member);

        if (requested.compareTo(BigDecimal.ZERO) <= 0) {
            if (existing.isPresent()) {
                restoreBalance(balance, existing.get().getAmount());
                ledgerRepository.delete(existing.get());
                balanceRepository.save(balance);
            }
            return BigDecimal.ZERO;
        }

        BigDecimal actualDebit = computeActualDebit(space, balance, requested);
        BigDecimal overflow = requested.subtract(actualDebit).max(BigDecimal.ZERO);

        if (existing.isPresent()) {
            BigDecimal previous = existing.get().getAmount();
            balance.setBalance(balance.getBalance().add(previous).subtract(actualDebit));
            existing.get().setAmount(actualDebit);
            existing.get().setRequestedAmount(requested);
            existing.get().setMealCount(charge.getMealCount());
            ledgerRepository.save(existing.get());
        } else {
            balance.setBalance(balance.getBalance().subtract(actualDebit));
            ledgerRepository.save(MemberMealBalanceLedgerEntryEntity.builder()
                    .balance(balance)
                    .entryType(MealBalanceLedgerEntryType.DEBIT)
                    .amount(actualDebit)
                    .requestedAmount(requested)
                    .mealCount(charge.getMealCount())
                    .pollId(poll.getId())
                    .pollDate(poll.getPollDate())
                    .mealType(poll.getMealType())
                    .idempotencyKey(idempotencyKey)
                    .build());
        }

        balanceRepository.save(balance);

        if (overflow.compareTo(BigDecimal.ZERO) > 0 && !space.isPrepaidFallbackToPayPerMeal()) {
            throw new BusinessException("Insufficient meal balance", HttpStatus.BAD_REQUEST);
        }

        return toCurrencyOverflow(overflow, charge, unit);
    }

    private BigDecimal toCurrencyOverflow(BigDecimal overflow, MealPollCharge charge, PrepaidBalanceUnit unit) {
        if (overflow.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (unit == PrepaidBalanceUnit.MEALS && charge.getMealCount() > 0) {
            return charge.getCurrencyTotal()
                    .multiply(overflow)
                    .divide(BigDecimal.valueOf(charge.getMealCount()), 2, RoundingMode.HALF_UP);
        }
        return overflow;
    }

    @Transactional(readOnly = true)
    public BigDecimal sumPaidForSpace(UUID spaceId, YearMonth month) {
        return ledgerRepository.sumPaidAmountBySpaceAndTypeInRange(
                spaceId,
                MealBalanceLedgerEntryType.PURCHASE,
                monthStart(month),
                monthEndExclusive(month));
    }

    @Transactional(readOnly = true)
    public BigDecimal sumPaid(UUID spaceId, UUID memberId, YearMonth month) {
        return ledgerRepository.sumPaidAmountByMemberAndTypeInRange(
                spaceId,
                memberId,
                MealBalanceLedgerEntryType.PURCHASE,
                monthStart(month),
                monthEndExclusive(month));
    }

    @Transactional(readOnly = true)
    public BigDecimal sumPurchasedForSpace(UUID spaceId, YearMonth month) {
        return ledgerRepository.sumAmountBySpaceAndTypeInRange(
                spaceId,
                MealBalanceLedgerEntryType.PURCHASE,
                monthStart(month),
                monthEndExclusive(month));
    }

    @Transactional(readOnly = true)
    public BigDecimal sumConsumedForSpace(UUID spaceId, YearMonth month) {
        return ledgerRepository.sumAmountBySpaceAndTypeInRange(
                spaceId,
                MealBalanceLedgerEntryType.DEBIT,
                monthStart(month),
                monthEndExclusive(month));
    }

    @Transactional(readOnly = true)
    public BigDecimal sumRemainingForSpace(UUID spaceId) {
        return balanceRepository.sumBalanceBySpaceId(spaceId);
    }

    @Transactional(readOnly = true)
    public MemberMealBalanceEntity findBalance(UUID spaceId, UUID memberId) {
        return balanceRepository.findBySpaceIdAndMemberId(spaceId, memberId).orElse(null);
    }

    @Transactional(readOnly = true)
    public BigDecimal sumPurchased(UUID spaceId, UUID memberId, YearMonth month) {
        return ledgerRepository.sumAmountByMemberAndTypeInRange(
                spaceId,
                memberId,
                MealBalanceLedgerEntryType.PURCHASE,
                monthStart(month),
                monthEndExclusive(month));
    }

    @Transactional(readOnly = true)
    public BigDecimal sumConsumed(UUID spaceId, UUID memberId, YearMonth month) {
        return ledgerRepository.sumAmountByMemberAndTypeInRange(
                spaceId,
                memberId,
                MealBalanceLedgerEntryType.DEBIT,
                monthStart(month),
                monthEndExclusive(month));
    }

    @Transactional(readOnly = true)
    public BigDecimal sumDebitedOnDate(UUID spaceId, UUID memberId, java.time.LocalDate date) {
        return ledgerRepository.sumAmountByMemberAndTypeInRange(
                spaceId,
                memberId,
                MealBalanceLedgerEntryType.DEBIT,
                date.atStartOfDay(),
                date.plusDays(1).atStartOfDay());
    }

    private BigDecimal computeActualDebit(SpaceEntity space, MemberMealBalanceEntity balance, BigDecimal requested) {
        if (space.isPrepaidFallbackToPayPerMeal()) {
            return requested.min(balance.getBalance());
        }
        if (balance.getBalance().compareTo(requested) < 0) {
            throw new BusinessException("Insufficient meal balance", HttpStatus.BAD_REQUEST);
        }
        return requested;
    }

    private MemberMealBalanceEntity getOrCreateBalance(SpaceEntity space, MemberEntity member) {
        return balanceRepository
                .findBySpaceIdAndMemberId(space.getId(), member.getId())
                .orElseGet(() -> balanceRepository.save(MemberMealBalanceEntity.builder()
                        .space(space)
                        .member(member)
                        .balance(BigDecimal.ZERO)
                        .unit(resolveUnit(space))
                        .currencyCode(DEFAULT_CURRENCY)
                        .build()));
    }

    private MemberMealBalanceEntity emptyBalance(UUID spaceId, UUID memberId) {
        SpaceEntity space = loadSpace(spaceId);
        MemberEntity member = validateMember(spaceId, memberId);
        return MemberMealBalanceEntity.builder()
                .space(space)
                .member(member)
                .balance(BigDecimal.ZERO)
                .unit(resolveUnit(space))
                .currencyCode(DEFAULT_CURRENCY)
                .build();
    }

    private PrepaidBalanceUnit resolveUnit(SpaceEntity space) {
        return space.getPrepaidBalanceUnit() != null ? space.getPrepaidBalanceUnit() : PrepaidBalanceUnit.MEALS;
    }

    private void restoreBalance(MemberMealBalanceEntity balance, BigDecimal amount) {
        balance.setBalance(balance.getBalance().add(amount));
    }

    private String debitKey(UUID pollId, UUID memberId) {
        return "DEBIT:poll:" + pollId + ":member:" + memberId;
    }

    private LocalDateTime monthStart(YearMonth month) {
        return month.atDay(1).atStartOfDay();
    }

    private LocalDateTime monthEndExclusive(YearMonth month) {
        return month.plusMonths(1).atDay(1).atStartOfDay();
    }

    private void requirePrepaidBilling(SpaceEntity space, MemberEntity member) {
        if (!mealBillingResolver.isPrepaid(space, member)) {
            throw new BusinessException(
                    "Meal balance purchases are only available for prepaid balance members",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private SpaceEntity loadSpace(UUID spaceId) {
        return spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));
    }

    private MemberEntity validateMember(UUID spaceId, UUID memberId) {
        return memberRepository
                .findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private BigDecimal resolvePaidAmount(
            RecordMealBalancePurchaseRequest request, PrepaidBalanceUnit unit, BigDecimal amount) {
        BigDecimal paidAmount = request.getPaidAmount();
        if (unit == PrepaidBalanceUnit.MEALS) {
            if (paidAmount == null || paidAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(
                        "Paid amount is required when selling meal packs", HttpStatus.BAD_REQUEST);
            }
            return paidAmount;
        }
        if (paidAmount != null && paidAmount.compareTo(BigDecimal.ZERO) > 0) {
            return paidAmount;
        }
        return amount;
    }
}
