package com.acomi.acomi_backend.payment.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.dashboard.api.dto.response.MemberPaymentLedgerResponse;
import com.acomi.acomi_backend.dashboard.api.dto.response.MemberPaymentLedgerRowResponse;
import com.acomi.acomi_backend.dashboard.application.service.DashboardAccessService;
import com.acomi.acomi_backend.dashboard.application.service.SpaceBillingService;
import com.acomi.acomi_backend.dashboard.application.support.PayPerMealBillingCalculator;
import com.acomi.acomi_backend.payment.application.support.PaymentMonthCountsSupport;
import com.acomi.acomi_backend.payment.api.dto.response.OwnerPaymentsMonthCountsResponse;
import com.acomi.acomi_backend.payment.api.dto.response.OwnerPaymentsMonthResponse;
import com.acomi.acomi_backend.payment.api.dto.response.SpacePaymentListResponse;
import com.acomi.acomi_backend.payment.api.dto.response.SpacePaymentResponse;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Legacy owner-month aggregate. Prefer {@link PaymentsQueryService} endpoints.
 * Reads are write-free. Use {@link #syncMonth} for explicit synchronization.
 */
@Service
@RequiredArgsConstructor
public class OwnerPaymentsMonthService {

    private final DashboardAccessService dashboardAccessService;
    private final SpaceBillingService spaceBillingService;
    private final SpacePaymentService spacePaymentService;
    private final SpacePaymentGenerationService generationService;
    private final MealDaySpacePaymentBridge mealDaySpacePaymentBridge;
    private final PaymentMonthSnapshotService snapshotService;
    private final PayPerMealBillingCalculator payPerMealBillingCalculator;

    /** Explicit sync command — mutations / manual refresh only. Never part of default GET. */
    @Transactional
    public void syncMonth(UUID spaceId, UUID callerId, String monthParam) {
        dashboardAccessService.requireManagePayments(spaceId, callerId);
        YearMonth month = parseMonth(monthParam);
        generationService.syncExpectedPayments(spaceId, callerId, month);
        mealDaySpacePaymentBridge.backfillPendingApprovalsForMonth(spaceId, month, callerId);
        snapshotService.rebuildMonth(spaceId, callerId, month);
    }

    /** Read-only aggregate. Callers that need sync must invoke {@link #syncMonth} first. */
    @Transactional(readOnly = true)
    public OwnerPaymentsMonthResponse buildOwnerMonth(
            UUID spaceId, UUID callerId, String monthParam) {
        dashboardAccessService.requireManagePayments(spaceId, callerId);
        YearMonth month = parseMonth(monthParam);

        payPerMealBillingCalculator.beginRequestCache();
        try {
            MemberPaymentLedgerResponse ledger =
                    spaceBillingService.buildLedger(spaceId, callerId, month.toString());

            SpacePaymentListResponse list = spacePaymentService.listPayments(
                    spaceId,
                    callerId,
                    month.toString(),
                    null,
                    null,
                    null,
                    null,
                    false,
                    false);

            List<SpacePaymentResponse> payments = list.getPayments();
            return OwnerPaymentsMonthResponse.builder()
                    .month(ledger.getMonth())
                    .spaceType(ledger.getSpaceType())
                    .summary(ledger.getSummary())
                    .members(ledger.getMembers())
                    .payments(payments)
                    .counts(computeCounts(payments, ledger.getMembers()))
                    .build();
        } finally {
            payPerMealBillingCalculator.endRequestCache();
        }
    }

    private OwnerPaymentsMonthCountsResponse computeCounts(
            List<SpacePaymentResponse> payments, List<MemberPaymentLedgerRowResponse> members) {
        return PaymentMonthCountsSupport.fromPaymentStatuses(
                payments.stream().map(SpacePaymentResponse::getPaymentStatus).toList(),
                PaymentMonthCountsSupport.countPendingMembers(members));
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
