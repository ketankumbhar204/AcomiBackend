package com.countin.countin_backend.payment.application.service;

import com.countin.countin_backend.dashboard.api.dto.response.DashboardFinancialSummaryResponse;
import com.countin.countin_backend.dashboard.api.dto.response.MemberPaymentLedgerResponse;
import com.countin.countin_backend.dashboard.api.dto.response.MemberPaymentLedgerRowResponse;
import com.countin.countin_backend.dashboard.api.dto.response.PrepaidBalanceSummaryResponse;
import com.countin.countin_backend.dashboard.application.service.SpaceBillingService;
import com.countin.countin_backend.dashboard.application.support.PayPerMealBillingCalculator;
import com.countin.countin_backend.dashboard.domain.model.MemberPaymentStatus;
import com.countin.countin_backend.payment.application.support.PaymentMonthCountsSupport;
import com.countin.countin_backend.payment.infrastructure.persistence.entity.SpacePaymentMemberMonthEntity;
import com.countin.countin_backend.payment.infrastructure.persistence.entity.SpacePaymentMonthSummaryEntity;
import com.countin.countin_backend.payment.infrastructure.persistence.repository.SpacePaymentMemberMonthRepository;
import com.countin.countin_backend.payment.infrastructure.persistence.repository.SpacePaymentMonthSummaryRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Materializes member-month ledger rows + financial summary for read-only Payments APIs.
 * Rebuilds on sync / payment mutations — never as part of expected-payment generation.
 */
@Service
@RequiredArgsConstructor
public class PaymentMonthSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(PaymentMonthSnapshotService.class);

    /** One rebuild at a time per space+month — concurrent GETs must not stampede buildLedger. */
    private final ConcurrentHashMap<String, Object> rebuildLocks = new ConcurrentHashMap<>();

    private final SpaceBillingService spaceBillingService;
    private final SpacePaymentMemberMonthRepository memberMonthRepository;
    private final SpacePaymentMonthSummaryRepository summaryRepository;
    private final PayPerMealBillingCalculator payPerMealBillingCalculator;

    @Transactional
    public void rebuildMonth(UUID spaceId, UUID callerId, YearMonth month) {
        String monthKey = month.toString();
        String lockKey = spaceId + "|" + monthKey;
        Object lock = rebuildLocks.computeIfAbsent(lockKey, ignored -> new Object());
        synchronized (lock) {
            rebuildMonthUnlocked(spaceId, callerId, month);
        }
    }

    /**
     * Lazily materialize snapshot when missing. Not payment sync — only caches ledger math.
     * Concurrent callers block on the same lock and then see the existing summary row.
     */
    @Transactional
    public void ensureMonth(UUID spaceId, UUID callerId, YearMonth month) {
        String monthKey = month.toString();
        if (summaryRepository.existsBySpaceIdAndMonth(spaceId, monthKey)) {
            return;
        }
        String lockKey = spaceId + "|" + monthKey;
        Object lock = rebuildLocks.computeIfAbsent(lockKey, ignored -> new Object());
        synchronized (lock) {
            if (summaryRepository.existsBySpaceIdAndMonth(spaceId, monthKey)) {
                return;
            }
            rebuildMonthUnlocked(spaceId, callerId, month);
        }
    }

    /** Caller must hold rebuildLocks entry for space+month. */
    private void rebuildMonthUnlocked(UUID spaceId, UUID callerId, YearMonth month) {
        String monthKey = month.toString();
        long started = System.currentTimeMillis();
        log.info("payment_snapshot_rebuild_start spaceId={} month={}", spaceId, monthKey);
        payPerMealBillingCalculator.beginRequestCache();
        try {
            MemberPaymentLedgerResponse ledger =
                    spaceBillingService.buildLedger(spaceId, callerId, monthKey);
            persistLedger(spaceId, ledger);
        } finally {
            payPerMealBillingCalculator.endRequestCache();
        }
        log.info(
                "payment_snapshot_rebuild_done spaceId={} month={} durationMs={}",
                spaceId,
                monthKey,
                System.currentTimeMillis() - started);
    }

    @Transactional
    public void refreshAfterMutation(UUID spaceId, UUID callerId, String month) {
        if (month == null || month.isBlank()) {
            return;
        }
        rebuildMonth(spaceId, callerId, YearMonth.parse(month));
    }

    @Transactional(readOnly = true)
    public SpacePaymentMonthSummaryEntity requireSummary(UUID spaceId, String month) {
        return summaryRepository
                .findBySpaceIdAndMonth(spaceId, month)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Page<SpacePaymentMemberMonthEntity> pageMembers(
            UUID spaceId,
            String month,
            String search,
            String statusOrPreset,
            String sort,
            int page,
            int size) {
        String searchTerm = search == null ? "" : search.trim();
        boolean searchBlank = searchTerm.isBlank();
        Set<MemberPaymentStatus> statuses = resolveStatusFilter(statusOrPreset);
        boolean statusesEmpty = statuses.isEmpty();
        boolean matchUnderReviewAmounts = statuses.contains(MemberPaymentStatus.UNDER_REVIEW);
        // Pending/Partial filters must exclude members whose residual is already covered by under review.
        boolean excludeCoveredUnderReview = statuses.contains(MemberPaymentStatus.PENDING)
                || statuses.contains(MemberPaymentStatus.PARTIAL);

        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize, resolveSort(sort));

        return memberMonthRepository.searchPaged(
                spaceId,
                month,
                searchTerm,
                searchBlank,
                statusesEmpty ? EnumSet.noneOf(MemberPaymentStatus.class) : statuses,
                statusesEmpty,
                matchUnderReviewAmounts,
                excludeCoveredUnderReview,
                pageable);
    }

    public static MemberPaymentLedgerRowResponse toRow(SpacePaymentMemberMonthEntity entity) {
        BigDecimal expected = entity.getExpectedCharges();
        BigDecimal collected = entity.getCollected();
        BigDecimal underReview = entity.getUnderReview();
        // Always recompute residual so stale snapshot pending cannot disagree with under-review KPIs.
        BigDecimal pending = computePendingAmount(expected, collected, underReview);
        MemberPaymentStatus status = reconcileStatus(expected, collected, underReview, entity.getStatus());
        return MemberPaymentLedgerRowResponse.builder()
                .memberId(entity.getMemberId())
                .memberName(entity.getMemberName())
                .expectedCharges(expected)
                .collected(collected)
                .underReview(underReview)
                .pending(pending)
                .currencyCode(entity.getCurrencyCode())
                .status(status)
                .mealBillingType(entity.getMealBillingType())
                .mealBalanceRemaining(entity.getMealBalanceRemaining())
                .mealBalancePurchased(entity.getMealBalancePurchased())
                .mealBalanceConsumed(entity.getMealBalanceConsumed())
                .mealBalanceUnit(entity.getMealBalanceUnit())
                .build();
    }

    /** Read-path correction for stale member-month snapshot rows (no DB write). */
    private static MemberPaymentStatus reconcileStatus(
            BigDecimal expected,
            BigDecimal collected,
            BigDecimal underReview,
            MemberPaymentStatus stored) {
        if (stored == MemberPaymentStatus.UPDATE_REQUESTED
                || stored == MemberPaymentStatus.REJECTED) {
            return stored;
        }
        BigDecimal pending = computePendingAmount(expected, collected, underReview);
        boolean monetaryResidual = pending != null && pending.compareTo(java.math.BigDecimal.ZERO) > 0;
        if (monetaryResidual) {
            boolean hasCollected = collected != null && collected.compareTo(java.math.BigDecimal.ZERO) > 0;
            boolean hasUnderReview =
                    underReview != null && underReview.compareTo(java.math.BigDecimal.ZERO) > 0;
            if (hasCollected && !hasUnderReview) {
                return MemberPaymentStatus.PARTIAL;
            }
            return MemberPaymentStatus.PENDING;
        }
        if (underReview != null && underReview.compareTo(java.math.BigDecimal.ZERO) > 0) {
            return MemberPaymentStatus.UNDER_REVIEW;
        }
        if (stored != null && stored != MemberPaymentStatus.NONE) {
            // Keep PAID / NONE from stored when amounts no longer force another state.
            if (stored == MemberPaymentStatus.PAID || stored == MemberPaymentStatus.NONE) {
                return stored;
            }
        }
        if (expected == null || expected.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return collected != null && collected.compareTo(java.math.BigDecimal.ZERO) > 0
                    ? MemberPaymentStatus.PAID
                    : MemberPaymentStatus.NONE;
        }
        if (collected == null || collected.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return MemberPaymentStatus.PENDING;
        }
        if (collected.compareTo(expected) >= 0) {
            return MemberPaymentStatus.PAID;
        }
        return MemberPaymentStatus.PARTIAL;
    }

    private static java.math.BigDecimal computePendingAmount(
            java.math.BigDecimal expected,
            java.math.BigDecimal collected,
            java.math.BigDecimal underReview) {
        if (expected == null) {
            return null;
        }
        java.math.BigDecimal collectedAmount =
                collected != null ? collected : java.math.BigDecimal.ZERO;
        java.math.BigDecimal underReviewAmount =
                underReview != null ? underReview : java.math.BigDecimal.ZERO;
        return expected.subtract(collectedAmount).subtract(underReviewAmount).max(java.math.BigDecimal.ZERO);
    }

    public static DashboardFinancialSummaryResponse toFinancial(SpacePaymentMonthSummaryEntity entity) {
        PrepaidBalanceSummaryResponse prepaid = null;
        if (entity.getPrepaidAmountCollected() != null
                || entity.getPrepaidMealsRemaining() != null
                || entity.getPrepaidUnit() != null) {
            prepaid = PrepaidBalanceSummaryResponse.builder()
                    .balanceRemaining(entity.getPrepaidMealsRemaining())
                    .amountCollected(entity.getPrepaidAmountCollected())
                    .unit(entity.getPrepaidUnit())
                    .currencyCode(entity.getPrepaidCurrencyCode())
                    .build();
        }
        return DashboardFinancialSummaryResponse.builder()
                .expectedCharges(entity.getExpectedCharges())
                .collected(entity.getCollected())
                .underReview(entity.getUnderReview())
                .pending(entity.getPending())
                .currencyCode(entity.getCurrencyCode())
                .source(entity.getFinancialSource())
                .mealBillingType(entity.getMealBillingType())
                .prepaidBalance(prepaid)
                .mixedMealBilling(entity.getMixedMealBilling())
                .build();
    }

    private void persistLedger(UUID spaceId, MemberPaymentLedgerResponse ledger) {
        String month = ledger.getMonth();
        memberMonthRepository.deleteBySpaceIdAndMonth(spaceId, month);

        List<SpacePaymentMemberMonthEntity> rows = new ArrayList<>(ledger.getMembers().size());
        for (MemberPaymentLedgerRowResponse row : ledger.getMembers()) {
            rows.add(SpacePaymentMemberMonthEntity.builder()
                    .spaceId(spaceId)
                    .memberId(row.getMemberId())
                    .month(month)
                    .memberName(row.getMemberName() != null ? row.getMemberName() : "")
                    .expectedCharges(row.getExpectedCharges())
                    .collected(row.getCollected())
                    .underReview(row.getUnderReview())
                    .pending(row.getPending())
                    .currencyCode(row.getCurrencyCode() != null ? row.getCurrencyCode() : "INR")
                    .status(row.getStatus() != null ? row.getStatus() : MemberPaymentStatus.NONE)
                    .mealBillingType(row.getMealBillingType())
                    .mealBalanceRemaining(row.getMealBalanceRemaining())
                    .mealBalancePurchased(row.getMealBalancePurchased())
                    .mealBalanceConsumed(row.getMealBalanceConsumed())
                    .mealBalanceUnit(row.getMealBalanceUnit())
                    .build());
        }
        memberMonthRepository.saveAll(rows);

        DashboardFinancialSummaryResponse financial = ledger.getSummary();
        PrepaidBalanceSummaryResponse prepaid =
                financial != null ? financial.getPrepaidBalance() : null;
        int pendingMembers = PaymentMonthCountsSupport.countPendingMembers(ledger.getMembers());

        SpacePaymentMonthSummaryEntity summary = summaryRepository
                .findBySpaceIdAndMonth(spaceId, month)
                .orElseGet(() -> SpacePaymentMonthSummaryEntity.builder()
                        .spaceId(spaceId)
                        .month(month)
                        .build());
        summary.setSpaceType(ledger.getSpaceType());
        summary.setExpectedCharges(financial != null ? financial.getExpectedCharges() : null);
        summary.setCollected(financial != null ? financial.getCollected() : null);
        summary.setUnderReview(financial != null ? financial.getUnderReview() : null);
        summary.setPending(financial != null ? financial.getPending() : null);
        summary.setCurrencyCode(
                financial != null && financial.getCurrencyCode() != null
                        ? financial.getCurrencyCode()
                        : "INR");
        summary.setFinancialSource(financial != null ? financial.getSource() : null);
        summary.setMealBillingType(financial != null ? financial.getMealBillingType() : null);
        summary.setMixedMealBilling(financial != null ? financial.getMixedMealBilling() : null);
        summary.setPrepaidMealsRemaining(prepaid != null ? prepaid.getBalanceRemaining() : null);
        summary.setPrepaidAmountCollected(prepaid != null ? prepaid.getAmountCollected() : null);
        summary.setPrepaidCurrencyCode(prepaid != null ? prepaid.getCurrencyCode() : null);
        summary.setPrepaidUnit(prepaid != null ? prepaid.getUnit() : null);
        summary.setPendingMembers(pendingMembers);
        summary.setMemberCount(ledger.getMembers().size());
        summaryRepository.save(summary);
    }

    private static Set<MemberPaymentStatus> resolveStatusFilter(String statusOrPreset) {
        if (statusOrPreset == null || statusOrPreset.isBlank() || "ALL".equalsIgnoreCase(statusOrPreset)) {
            return EnumSet.noneOf(MemberPaymentStatus.class);
        }
        String key = statusOrPreset.trim().toUpperCase(Locale.ROOT);
        return switch (key) {
            case "PENDING", "PRESET_PENDING" -> EnumSet.copyOf(PaymentMonthCountsSupport.PENDING_MEMBER_STATUSES);
            case "COLLECTED", "PRESET_COLLECTED" -> EnumSet.of(
                    MemberPaymentStatus.PAID, MemberPaymentStatus.PARTIAL);
            case "UNDER_REVIEW", "PRESET_UNDER_REVIEW" -> EnumSet.of(MemberPaymentStatus.UNDER_REVIEW);
            default -> {
                // Comma-separated statuses or single status name
                EnumSet<MemberPaymentStatus> set = EnumSet.noneOf(MemberPaymentStatus.class);
                for (String part : key.split(",")) {
                    String token = part.trim();
                    if (token.isEmpty()) {
                        continue;
                    }
                    try {
                        set.add(MemberPaymentStatus.valueOf(token));
                    } catch (IllegalArgumentException ignored) {
                        // ignore unknown tokens
                    }
                }
                yield set;
            }
        };
    }

    private static Sort resolveSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return defaultPendingSort();
        }
        return switch (sort.trim().toLowerCase(Locale.ROOT)) {
            case "name_asc" -> Sort.by(Sort.Order.asc("memberName"));
            case "name_desc" -> Sort.by(Sort.Order.desc("memberName"));
            case "due_desc", "pending_desc" -> defaultPendingSort();
            default -> defaultPendingSort();
        };
    }

    private static Sort defaultPendingSort() {
        return Sort.by(
                new Sort.Order(Sort.Direction.DESC, "pending", Sort.NullHandling.NULLS_LAST),
                Sort.Order.asc("memberName"));
    }
}
