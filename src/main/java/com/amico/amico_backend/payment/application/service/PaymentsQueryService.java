package com.amico.amico_backend.payment.application.service;

import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.common.web.PagedResponse;
import com.amico.amico_backend.dashboard.api.dto.response.DashboardFinancialSummaryResponse;
import com.amico.amico_backend.dashboard.api.dto.response.MemberPaymentLedgerRowResponse;
import com.amico.amico_backend.dashboard.application.service.DashboardAccessService;
import com.amico.amico_backend.dashboard.domain.model.MemberPaymentStatus;
import com.amico.amico_backend.meal.application.support.MealPricingPolicy;
import com.amico.amico_backend.payment.api.dto.response.OwnerPaymentsMonthCountsResponse;
import com.amico.amico_backend.payment.api.dto.response.PaymentsCardsPageResponse;
import com.amico.amico_backend.payment.api.dto.response.PaymentsMembersPageResponse;
import com.amico.amico_backend.payment.api.dto.response.PaymentsSummaryResponse;
import com.amico.amico_backend.payment.api.dto.response.SpacePaymentResponse;
import com.amico.amico_backend.payment.application.support.PaymentMonthCountsSupport;
import com.amico.amico_backend.payment.domain.model.SpacePaymentStatus;
import com.amico.amico_backend.payment.domain.model.SpacePaymentType;
import com.amico.amico_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.amico.amico_backend.payment.infrastructure.persistence.entity.SpacePaymentMemberMonthEntity;
import com.amico.amico_backend.payment.infrastructure.persistence.entity.SpacePaymentMonthSummaryEntity;
import com.amico.amico_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.amico.amico_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only Payments module queries (summary / members / review / history).
 * Never runs syncExpectedPayments or meal-day backfill.
 */
@Service
@RequiredArgsConstructor
public class PaymentsQueryService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(PaymentsQueryService.class);

    public static final String QUEUE_SUBMITTED = "SUBMITTED";
    public static final String QUEUE_NEEDS_UPDATE = "NEEDS_UPDATE";
    public static final String QUEUE_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String QUEUE_PAID = "PAID";
    public static final String QUEUE_REJECTED = "REJECTED";
    public static final String QUEUE_HISTORY = "HISTORY";

    private final DashboardAccessService dashboardAccessService;
    private final SpacePaymentRepository paymentRepository;
    private final SpacePaymentService spacePaymentService;
    private final SpaceRepository spaceRepository;
    private final PaymentMonthSnapshotService snapshotService;

    @Transactional
    public PaymentsSummaryResponse getSummary(UUID spaceId, UUID callerId, String monthParam) {
        long started = System.currentTimeMillis();
        dashboardAccessService.requireManagePayments(spaceId, callerId);
        YearMonth month = parseMonth(monthParam);
        // Only summary may materialize — never let members/review stampede rebuild in parallel.
        snapshotService.ensureMonth(spaceId, callerId, month);

        SpacePaymentMonthSummaryEntity summaryRow =
                snapshotService.requireSummary(spaceId, month.toString());
        SpaceEntity space = spaceRepository.findById(spaceId).orElse(null);

        DashboardFinancialSummaryResponse financial = summaryRow != null
                ? PaymentMonthSnapshotService.toFinancial(summaryRow)
                : DashboardFinancialSummaryResponse.builder().currencyCode("INR").build();

        int pendingMembers = summaryRow != null ? summaryRow.getPendingMembers() : 0;
        OwnerPaymentsMonthCountsResponse counts = buildCountsFromDb(spaceId, month.toString(), pendingMembers);

        PaymentsSummaryResponse response = PaymentsSummaryResponse.builder()
                .month(month.toString())
                .spaceType(summaryRow != null
                        ? summaryRow.getSpaceType()
                        : (space != null ? space.getType() : null))
                .financial(financial)
                .counts(counts)
                .build();
        log.info(
                "payments_summary_done spaceId={} month={} durationMs={} hasSnapshot={}",
                spaceId,
                month,
                System.currentTimeMillis() - started,
                summaryRow != null);
        return response;
    }

    @Transactional(readOnly = true)
    public PaymentsMembersPageResponse getMembers(
            UUID spaceId,
            UUID callerId,
            String monthParam,
            String search,
            String statusFilter,
            String sort,
            int page,
            int size) {
        long started = System.currentTimeMillis();
        dashboardAccessService.requireManagePayments(spaceId, callerId);
        YearMonth month = parseMonth(monthParam);
        // Read-only. Snapshot must already exist (summary ensure or POST /sync / mutation rebuild).
        // Do not call ensureMonth here — it caused parallel full-ledger rebuilds under load.

        Page<SpacePaymentMemberMonthEntity> entityPage = snapshotService.pageMembers(
                spaceId, month.toString(), search, statusFilter, sort, page, size);

        boolean collectedPreset = statusFilter != null
                && ("COLLECTED".equalsIgnoreCase(statusFilter.trim())
                        || "PRESET_COLLECTED".equalsIgnoreCase(statusFilter.trim()));

        List<MemberPaymentLedgerRowResponse> content = entityPage.getContent().stream()
                .map(PaymentMonthSnapshotService::toRow)
                // Defense in depth: reconciled status can differ from stored snapshot status.
                .filter(row -> !collectedPreset || isCollectedMemberRow(row))
                .toList();
        Page<MemberPaymentLedgerRowResponse> mapped =
                new PageImpl<>(content, entityPage.getPageable(), collectedPreset ? content.size() : entityPage.getTotalElements());

        PaymentsMembersPageResponse response = PaymentsMembersPageResponse.builder()
                .month(month.toString())
                .page(PagedResponse.from(mapped))
                .build();
        log.info(
                "payments_members_done spaceId={} month={} page={} size={} total={} statusFilter={} durationMs={}",
                spaceId,
                month,
                page,
                size,
                mapped.getTotalElements(),
                statusFilter,
                System.currentTimeMillis() - started);
        return response;
    }

    /** Collected filter aligns with summary KPI: any member with collected > 0. */
    private static boolean isCollectedMemberRow(MemberPaymentLedgerRowResponse row) {
        return row != null
                && row.getCollected() != null
                && row.getCollected().signum() > 0;
    }

    @Transactional(readOnly = true)
    public PaymentsCardsPageResponse getPaymentCards(
            UUID spaceId,
            UUID callerId,
            String monthParam,
            String queue,
            int page,
            int size) {
        dashboardAccessService.requireManagePayments(spaceId, callerId);
        YearMonth month = parseMonth(monthParam);
        String resolvedQueue =
                queue == null || queue.isBlank() ? QUEUE_PENDING_REVIEW : queue.trim().toUpperCase(Locale.ROOT);
        Set<SpacePaymentStatus> statuses = statusesForQueue(resolvedQueue);

        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Order.asc("dueDate"), Sort.Order.asc("member.fullName")));

        boolean statusesEmpty = statuses.isEmpty();
        SpaceEntity space = spaceRepository.findById(spaceId).orElse(null);
        boolean hideMeals = space != null && !MealPricingPolicy.usesSeparateMealBilling(space);
        SpacePaymentType excludeType = hideMeals ? SpacePaymentType.MEAL : null;

        Page<SpacePaymentEntity> entityPage = paymentRepository.searchPaged(
                spaceId,
                month.toString(),
                null,
                statusesEmpty ? EnumSet.noneOf(SpacePaymentStatus.class) : statuses,
                statusesEmpty,
                null,
                excludeType,
                null,
                pageable);

        List<UUID> ids = entityPage.getContent().stream().map(SpacePaymentEntity::getId).toList();
        Map<UUID, SpacePaymentEntity> byId = ids.isEmpty()
                ? Map.of()
                : paymentRepository.findAllByIdInWithGraph(ids).stream()
                        .collect(Collectors.toMap(SpacePaymentEntity::getId, p -> p, (a, b) -> a));

        List<SpacePaymentResponse> content = new ArrayList<>();
        for (UUID id : ids) {
            SpacePaymentEntity entity = byId.get(id);
            if (entity == null) {
                continue;
            }
            content.add(spacePaymentService.toPublicResponse(entity));
        }

        Page<SpacePaymentResponse> mapped =
                new PageImpl<>(content, pageable, entityPage.getTotalElements());

        return PaymentsCardsPageResponse.builder()
                .month(month.toString())
                .queue(resolvedQueue)
                .page(PagedResponse.from(mapped))
                .build();
    }

    private OwnerPaymentsMonthCountsResponse buildCountsFromDb(
            UUID spaceId, String month, int pendingMembers) {
        Map<SpacePaymentStatus, Long> byStatus = new HashMap<>();
        for (Object[] row : paymentRepository.countGroupedByStatus(spaceId, month)) {
            byStatus.put((SpacePaymentStatus) row[0], (Long) row[1]);
        }
        return PaymentMonthCountsSupport.fromStatusMap(byStatus, pendingMembers);
    }

    private static Set<SpacePaymentStatus> statusesForQueue(String queue) {
        return switch (queue) {
            case QUEUE_SUBMITTED -> EnumSet.of(
                    SpacePaymentStatus.UNDER_REVIEW, SpacePaymentStatus.PROOF_UPLOADED);
            case QUEUE_NEEDS_UPDATE -> EnumSet.of(SpacePaymentStatus.UPDATE_REQUESTED);
            case QUEUE_PENDING_REVIEW -> EnumSet.of(
                    SpacePaymentStatus.UNDER_REVIEW,
                    SpacePaymentStatus.PROOF_UPLOADED,
                    SpacePaymentStatus.UPDATE_REQUESTED);
            case QUEUE_PAID -> EnumSet.of(SpacePaymentStatus.PAID);
            case QUEUE_REJECTED -> EnumSet.of(SpacePaymentStatus.REJECTED);
            case QUEUE_HISTORY -> EnumSet.of(SpacePaymentStatus.PAID, SpacePaymentStatus.REJECTED);
            default -> throw new BusinessException(
                    "Invalid queue. Use SUBMITTED, NEEDS_UPDATE, PENDING_REVIEW, PAID, REJECTED, HISTORY",
                    HttpStatus.BAD_REQUEST);
        };
    }

    private YearMonth parseMonth(String monthParam) {
        if (monthParam == null || monthParam.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(monthParam);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("Invalid month format. Expected YYYY-MM", HttpStatus.BAD_REQUEST);
        }
    }
}
