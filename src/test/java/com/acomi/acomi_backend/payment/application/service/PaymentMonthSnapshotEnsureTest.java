package com.acomi.acomi_backend.payment.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.dashboard.api.dto.response.DashboardFinancialSummaryResponse;
import com.acomi.acomi_backend.dashboard.api.dto.response.MemberPaymentLedgerResponse;
import com.acomi.acomi_backend.dashboard.application.service.DashboardAccessService;
import com.acomi.acomi_backend.dashboard.application.service.SpaceBillingService;
import com.acomi.acomi_backend.dashboard.application.support.PayPerMealBillingCalculator;
import com.acomi.acomi_backend.dashboard.application.support.SpacePaymentLedgerSupport;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentMonthSummaryEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentMemberMonthRepository;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentMonthSummaryRepository;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentMonthSnapshotEnsureTest {

    @Mock private SpaceBillingService spaceBillingService;
    @Mock private SpacePaymentMemberMonthRepository memberMonthRepository;
    @Mock private SpacePaymentMonthSummaryRepository summaryRepository;
    @Mock private SpacePaymentRepository paymentRepository;
    @Mock private SpacePaymentLedgerSupport spacePaymentLedgerSupport;
    @Mock private PayPerMealBillingCalculator payPerMealBillingCalculator;
    @Mock private SpacePaymentGenerationService paymentGenerationService;
    @Mock private DashboardAccessService dashboardAccessService;
    @Mock private OccupancyRepository occupancyRepository;

    private PaymentMonthSnapshotService service;
    private UUID spaceId;
    private UUID callerId;

    @BeforeEach
    void setUp() {
        service = new PaymentMonthSnapshotService(
                spaceBillingService,
                memberMonthRepository,
                summaryRepository,
                paymentRepository,
                spacePaymentLedgerSupport,
                payPerMealBillingCalculator,
                paymentGenerationService,
                dashboardAccessService,
                occupancyRepository);
        spaceId = UUID.randomUUID();
        callerId = UUID.randomUUID();
    }

    @Test
    void ensureMonth_rebuildsWhenSnapshotExpectedIsEmptyButStayHasRent() {
        YearMonth month = YearMonth.of(2026, 10);
        SpacePaymentMonthSummaryEntity summary = SpacePaymentMonthSummaryEntity.builder()
                .spaceId(spaceId)
                .month("2026-10")
                .expectedCharges(null)
                .build();
        OccupancyEntity occupancy = OccupancyEntity.builder()
                .status(OccupancyStatus.ACTIVE)
                .moveInDate(LocalDate.of(2026, 10, 3))
                .actualMoveInAt(LocalDateTime.of(2026, 10, 3, 10, 0))
                .rentSnapshot(new BigDecimal("6000"))
                .depositSnapshot(new BigDecimal("3000"))
                .build();

        when(summaryRepository.existsBySpaceIdAndMonth(spaceId, "2026-10")).thenReturn(true);
        when(summaryRepository.findBySpaceIdAndMonth(spaceId, "2026-10")).thenReturn(Optional.of(summary));
        when(paymentRepository.findBySpaceIdAndMonth(spaceId, "2026-10")).thenReturn(List.of());
        when(spacePaymentLedgerSupport.sumUnderReviewAmount(any())).thenReturn(BigDecimal.ZERO);
        when(occupancyRepository.findBillableBySpaceIdForMonth(eq(spaceId), any(), any(), any()))
                .thenReturn(List.of(occupancy));
        when(dashboardAccessService.canManagePayments(spaceId, callerId)).thenReturn(true);
        when(spaceBillingService.buildLedger(spaceId, callerId, "2026-10"))
                .thenReturn(MemberPaymentLedgerResponse.builder()
                        .month("2026-10")
                        .members(List.of())
                        .summary(DashboardFinancialSummaryResponse.builder()
                                .expectedCharges(new BigDecimal("5612.90"))
                                .pending(new BigDecimal("5612.90"))
                                .currencyCode("INR")
                                .build())
                        .build());
        when(memberMonthRepository.saveAll(any())).thenReturn(List.of());
        when(summaryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.ensureMonth(spaceId, callerId, month);

        verify(paymentGenerationService).syncExpectedPayments(spaceId, callerId, month);
        verify(spaceBillingService).buildLedger(spaceId, callerId, "2026-10");
    }

    @Test
    void ensureMonth_doesNotRebuildWhenExpectedAlreadyRecorded() {
        YearMonth month = YearMonth.of(2026, 10);
        SpacePaymentMonthSummaryEntity summary = SpacePaymentMonthSummaryEntity.builder()
                .spaceId(spaceId)
                .month("2026-10")
                .expectedCharges(new BigDecimal("6000"))
                .build();
        when(summaryRepository.existsBySpaceIdAndMonth(spaceId, "2026-10")).thenReturn(true);
        when(summaryRepository.findBySpaceIdAndMonth(spaceId, "2026-10")).thenReturn(Optional.of(summary));
        when(paymentRepository.findBySpaceIdAndMonth(spaceId, "2026-10")).thenReturn(List.of());
        when(spacePaymentLedgerSupport.sumUnderReviewAmount(any())).thenReturn(BigDecimal.ZERO);

        service.ensureMonth(spaceId, callerId, month);

        verify(paymentGenerationService, never()).syncExpectedPayments(any(), any(), any());
        verify(spaceBillingService, never()).buildLedger(any(), any(), any());
    }

    @Test
    void ensureMonth_createsMissingRentWhenSummaryAlreadyCountsTheContract() {
        YearMonth month = YearMonth.of(2026, 10);
        SpacePaymentMonthSummaryEntity summary = SpacePaymentMonthSummaryEntity.builder()
                .spaceId(spaceId)
                .month("2026-10")
                .expectedCharges(new BigDecimal("5000"))
                .pending(new BigDecimal("5000"))
                .build();
        MemberEntity member = MemberEntity.builder().fullName("Pre 26130").build();
        member.setId(UUID.randomUUID());
        OccupancyEntity occupancy = OccupancyEntity.builder()
                .status(OccupancyStatus.ACTIVE)
                .member(member)
                .moveInDate(LocalDate.of(2026, 9, 12))
                .actualMoveInAt(LocalDateTime.of(2026, 9, 12, 10, 0))
                .rentSnapshot(new BigDecimal("5000"))
                .build();

        when(summaryRepository.existsBySpaceIdAndMonth(spaceId, "2026-10")).thenReturn(true);
        when(summaryRepository.findBySpaceIdAndMonth(spaceId, "2026-10")).thenReturn(Optional.of(summary));
        when(paymentRepository.findBySpaceIdAndMonth(spaceId, "2026-10")).thenReturn(List.of());
        when(spacePaymentLedgerSupport.sumUnderReviewAmount(any())).thenReturn(BigDecimal.ZERO);
        when(occupancyRepository.findBillableBySpaceIdForMonth(eq(spaceId), any(), any(), any()))
                .thenReturn(List.of(occupancy));
        when(dashboardAccessService.canManagePayments(spaceId, callerId)).thenReturn(true);
        when(spaceBillingService.buildLedger(spaceId, callerId, "2026-10"))
                .thenReturn(MemberPaymentLedgerResponse.builder()
                        .month("2026-10")
                        .members(List.of())
                        .summary(DashboardFinancialSummaryResponse.builder()
                                .expectedCharges(new BigDecimal("5000"))
                                .pending(new BigDecimal("5000"))
                                .currencyCode("INR")
                                .build())
                        .build());
        when(memberMonthRepository.saveAll(any())).thenReturn(List.of());
        when(summaryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.ensureMonth(spaceId, callerId, month);

        verify(paymentGenerationService).syncExpectedPayments(spaceId, callerId, month);
        verify(spaceBillingService).buildLedger(spaceId, callerId, "2026-10");
    }

    @Test
    void ensureMonth_leavesAMonthAloneWhenItsRentChargeAlreadyExists() {
        YearMonth month = YearMonth.of(2026, 9);
        UUID memberId = UUID.randomUUID();
        MemberEntity member = MemberEntity.builder().fullName("Pre 26130").build();
        member.setId(memberId);
        SpacePaymentMonthSummaryEntity summary = SpacePaymentMonthSummaryEntity.builder()
                .spaceId(spaceId)
                .month("2026-09")
                .expectedCharges(new BigDecimal("3166.67"))
                .pending(new BigDecimal("3166.67"))
                .build();
        SpacePaymentEntity rent = SpacePaymentEntity.builder()
                .member(member)
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.MONTHLY)
                .amount(new BigDecimal("3166.67"))
                .month("2026-09")
                .build();
        OccupancyEntity occupancy = OccupancyEntity.builder()
                .status(OccupancyStatus.ACTIVE)
                .member(member)
                .moveInDate(LocalDate.of(2026, 9, 12))
                .actualMoveInAt(LocalDateTime.of(2026, 9, 12, 10, 0))
                .rentSnapshot(new BigDecimal("5000"))
                .build();

        when(summaryRepository.existsBySpaceIdAndMonth(spaceId, "2026-09")).thenReturn(true);
        when(summaryRepository.findBySpaceIdAndMonth(spaceId, "2026-09")).thenReturn(Optional.of(summary));
        when(paymentRepository.findBySpaceIdAndMonth(spaceId, "2026-09")).thenReturn(List.of(rent));
        when(spacePaymentLedgerSupport.sumUnderReviewAmount(any())).thenReturn(BigDecimal.ZERO);
        when(occupancyRepository.findBillableBySpaceIdForMonth(eq(spaceId), any(), any(), any()))
                .thenReturn(List.of(occupancy));

        service.ensureMonth(spaceId, callerId, month);

        verify(paymentGenerationService, never()).syncExpectedPayments(any(), any(), any());
        verify(spaceBillingService, never()).buildLedger(any(), any(), any());
    }
}
