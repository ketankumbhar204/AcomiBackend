package com.acomi.acomi_backend.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.dashboard.api.dto.response.DashboardFinancialSummaryResponse;
import com.acomi.acomi_backend.dashboard.api.dto.response.MemberPaymentLedgerResponse;
import com.acomi.acomi_backend.dashboard.api.dto.response.MemberPaymentLedgerRowResponse;
import com.acomi.acomi_backend.dashboard.application.service.DashboardAccessService;
import com.acomi.acomi_backend.dashboard.application.service.SpaceBillingService;
import com.acomi.acomi_backend.dashboard.application.support.PayPerMealBillingCalculator;
import com.acomi.acomi_backend.dashboard.domain.model.MemberPaymentStatus;
import com.acomi.acomi_backend.payment.api.dto.response.OwnerPaymentsMonthResponse;
import com.acomi.acomi_backend.payment.api.dto.response.SpacePaymentListResponse;
import com.acomi.acomi_backend.payment.api.dto.response.SpacePaymentResponse;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OwnerPaymentsMonthServiceTest {

    @Mock
    private DashboardAccessService dashboardAccessService;
    @Mock
    private SpaceBillingService spaceBillingService;
    @Mock
    private SpacePaymentService spacePaymentService;
    @Mock
    private SpacePaymentGenerationService generationService;
    @Mock
    private MealDaySpacePaymentBridge mealDaySpacePaymentBridge;
    @Mock
    private PayPerMealBillingCalculator payPerMealBillingCalculator;
    @Mock
    private PaymentMonthSnapshotService snapshotService;

    private OwnerPaymentsMonthService service;

    private final UUID spaceId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OwnerPaymentsMonthService(
                dashboardAccessService,
                spaceBillingService,
                spacePaymentService,
                generationService,
                mealDaySpacePaymentBridge,
                snapshotService,
                payPerMealBillingCalculator);
    }

    @Test
    void buildOwnerMonthIsReadOnlyAndDoesNotSync() {
        MemberPaymentLedgerRowResponse member = MemberPaymentLedgerRowResponse.builder()
                .memberId(UUID.randomUUID())
                .memberName("Customer One")
                .expectedCharges(new BigDecimal("750"))
                .pending(new BigDecimal("570"))
                .underReview(new BigDecimal("180"))
                .status(MemberPaymentStatus.PENDING)
                .currencyCode("INR")
                .build();

        when(spaceBillingService.buildLedger(eq(spaceId), eq(callerId), eq("2026-07")))
                .thenReturn(MemberPaymentLedgerResponse.builder()
                        .month("2026-07")
                        .spaceType(SpaceType.MESS)
                        .summary(DashboardFinancialSummaryResponse.builder()
                                .expectedCharges(new BigDecimal("750"))
                                .pending(new BigDecimal("570"))
                                .underReview(new BigDecimal("180"))
                                .currencyCode("INR")
                                .build())
                        .members(List.of(member))
                        .build());

        when(spacePaymentService.listPayments(
                        eq(spaceId),
                        eq(callerId),
                        eq("2026-07"),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(false),
                        eq(false)))
                .thenReturn(SpacePaymentListResponse.builder()
                        .month("2026-07")
                        .payments(List.of(SpacePaymentResponse.builder()
                                .paymentId(UUID.randomUUID())
                                .paymentStatus(SpacePaymentStatus.UNDER_REVIEW)
                                .amount(new BigDecimal("180"))
                                .build()))
                        .build());

        OwnerPaymentsMonthResponse response = service.buildOwnerMonth(spaceId, callerId, "2026-07");

        assertThat(response.getMonth()).isEqualTo("2026-07");
        assertThat(response.getCounts().getSubmitted()).isEqualTo(1);
        verify(generationService, never()).syncExpectedPayments(any(), any(), any());
        verify(mealDaySpacePaymentBridge, never())
                .backfillPendingApprovalsForMonth(any(), any(), any());
        verify(payPerMealBillingCalculator).beginRequestCache();
        verify(payPerMealBillingCalculator).endRequestCache();
    }
}
