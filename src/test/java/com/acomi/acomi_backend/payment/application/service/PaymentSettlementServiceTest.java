package com.acomi.acomi_backend.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.occupancy.api.dto.request.ReservationPaymentSettlementDecision;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.payment.api.dto.request.CancelSpacePaymentRequest;
import com.acomi.acomi_backend.payment.api.dto.request.SettleSpacePaymentRequest;
import com.acomi.acomi_backend.payment.domain.model.PaymentSettlementAction;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.PaymentSettlementEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.PaymentSettlementRepository;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentSettlementServiceTest {

    @Mock
    private SpacePaymentRepository paymentRepository;

    @Mock
    private PaymentSettlementRepository settlementRepository;

    @Mock
    private SpacePaymentTimelineService timelineService;

    @Mock
    private SpacePaymentAccessService accessService;

    @Mock
    private PaymentMonthSnapshotService snapshotService;

    @InjectMocks
    private PaymentSettlementService settlementService;

    private UUID spaceId;
    private UUID callerId;
    private SpaceEntity space;
    private MemberEntity member;
    private OccupancyEntity occupancy;

    @BeforeEach
    void setUp() {
        spaceId = UUID.randomUUID();
        callerId = UUID.randomUUID();
        space = SpaceEntity.builder().build();
        space.setId(spaceId);
        member = MemberEntity.builder().fullName("Test Member").space(space).build();
        member.setId(UUID.randomUUID());
        occupancy = OccupancyEntity.builder()
                .space(space)
                .member(member)
                .status(OccupancyStatus.RESERVED)
                .build();
        occupancy.setId(UUID.randomUUID());
        lenient().when(accessService.requireManagePayments(spaceId, callerId)).thenReturn(null);
        lenient()
                .when(settlementRepository.findByPayment_IdInOrderByPerformedAtAsc(any()))
                .thenReturn(List.of());
        lenient().when(settlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(paymentRepository.save(any())).thenAnswer(inv -> {
            SpacePaymentEntity p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            return p;
        });
    }

    @Test
    void cancelUnpaidMarksCancelledAndRecordsSettlement() {
        SpacePaymentEntity pending = obligation(SpacePaymentType.RENT, SpacePaymentCategory.MONTHLY, "4000", false);
        when(paymentRepository.findByIdAndSpaceId(pending.getId(), spaceId)).thenReturn(Optional.of(pending));

        CancelSpacePaymentRequest request = new CancelSpacePaymentRequest();
        request.setReason("No longer needed");
        settlementService.cancelUnpaidPayment(spaceId, pending.getId(), callerId, request);

        assertThat(pending.getPaymentStatus()).isEqualTo(SpacePaymentStatus.CANCELLED);
        ArgumentCaptor<PaymentSettlementEntity> captor = ArgumentCaptor.forClass(PaymentSettlementEntity.class);
        verify(settlementRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo(PaymentSettlementAction.CANCEL_UNPAID);
    }

    @Test
    void cancelPaidRejected() {
        SpacePaymentEntity paid = obligation(SpacePaymentType.RENT, SpacePaymentCategory.ADVANCE, "2000", true);
        when(paymentRepository.findByIdAndSpaceId(paid.getId(), spaceId)).thenReturn(Optional.of(paid));

        assertThatThrownBy(() -> settlementService.cancelUnpaidPayment(
                        spaceId, paid.getId(), callerId, new CancelSpacePaymentRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Collected");
    }

    @Test
    void fullRefundCreatesRefundRow() {
        SpacePaymentEntity paid = obligation(SpacePaymentType.RENT, SpacePaymentCategory.ADVANCE, "2000", true);
        when(paymentRepository.findByIdAndSpaceId(paid.getId(), spaceId)).thenReturn(Optional.of(paid));

        SettleSpacePaymentRequest request = new SettleSpacePaymentRequest();
        request.setAction(PaymentSettlementAction.FULL_REFUND);
        request.setReason("Guest cancelled");
        settlementService.settleCollectedPayment(spaceId, paid.getId(), callerId, request);

        ArgumentCaptor<SpacePaymentEntity> paymentCaptor = ArgumentCaptor.forClass(SpacePaymentEntity.class);
        verify(paymentRepository, org.mockito.Mockito.atLeastOnce()).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getAllValues())
                .anySatisfy(row -> {
                    assertThat(row.getPaymentCategory()).isEqualTo(SpacePaymentCategory.REFUND);
                    assertThat(row.getAmount()).isEqualByComparingTo("2000");
                    assertThat(row.getPaymentStatus()).isEqualTo(SpacePaymentStatus.PAID);
                    assertThat(row.getRelatedPayment()).isEqualTo(paid);
                });
        assertThat(paid.getPaymentStatus()).isEqualTo(SpacePaymentStatus.REFUNDED);
    }

    @Test
    void partialRefundAndRetainValidatesSum() {
        SpacePaymentEntity paid = obligation(SpacePaymentType.DEPOSIT, SpacePaymentCategory.SECURITY, "5000", true);
        when(paymentRepository.findByIdAndSpaceId(paid.getId(), spaceId)).thenReturn(Optional.of(paid));

        SettleSpacePaymentRequest bad = new SettleSpacePaymentRequest();
        bad.setAction(PaymentSettlementAction.PARTIAL_REFUND);
        bad.setRefundAmount(new BigDecimal("4000"));
        bad.setRetainedAmount(new BigDecimal("2000"));
        bad.setReason("Deduction");
        assertThatThrownBy(() -> settlementService.settleCollectedPayment(spaceId, paid.getId(), callerId, bad))
                .isInstanceOf(BusinessException.class);

        SettleSpacePaymentRequest ok = new SettleSpacePaymentRequest();
        ok.setAction(PaymentSettlementAction.PARTIAL_REFUND);
        ok.setRefundAmount(new BigDecimal("3000"));
        ok.setRetainedAmount(new BigDecimal("2000"));
        ok.setReason("Damage deduction");
        settlementService.settleCollectedPayment(spaceId, paid.getId(), callerId, ok);

        ArgumentCaptor<PaymentSettlementEntity> captor = ArgumentCaptor.forClass(PaymentSettlementEntity.class);
        verify(settlementRepository).save(captor.capture());
        assertThat(captor.getValue().getRefundAmount()).isEqualByComparingTo("3000");
        assertThat(captor.getValue().getRetainedAmount()).isEqualByComparingTo("2000");
    }

    @Test
    void paymentLevelPartialRefundDoesNotAutoRetainOrMarkRefunded() {
        SpacePaymentEntity paid = obligation(SpacePaymentType.RENT, SpacePaymentCategory.ADVANCE, "5000", true);
        when(paymentRepository.findByIdAndSpaceId(paid.getId(), spaceId)).thenReturn(Optional.of(paid));

        SettleSpacePaymentRequest request = new SettleSpacePaymentRequest();
        request.setAction(PaymentSettlementAction.PARTIAL_REFUND);
        request.setRefundAmount(new BigDecimal("2000"));
        request.setReason("Partial return");
        settlementService.settleCollectedPayment(spaceId, paid.getId(), callerId, request);

        assertThat(paid.getPaymentStatus()).isEqualTo(SpacePaymentStatus.PAID);
        assertThat(paid.getRefundedAmount()).isEqualByComparingTo("2000");
        assertThat(paid.getRetainedAmount()).isEqualByComparingTo("0");
    }

    @Test
    void fullRefundMarksPaymentRefunded() {
        SpacePaymentEntity paid = obligation(SpacePaymentType.RENT, SpacePaymentCategory.ADVANCE, "2000", true);
        when(paymentRepository.findByIdAndSpaceId(paid.getId(), spaceId)).thenReturn(Optional.of(paid));

        SettleSpacePaymentRequest request = new SettleSpacePaymentRequest();
        request.setAction(PaymentSettlementAction.FULL_REFUND);
        request.setReason("Guest cancelled");
        settlementService.settleCollectedPayment(spaceId, paid.getId(), callerId, request);

        assertThat(paid.getPaymentStatus()).isEqualTo(SpacePaymentStatus.REFUNDED);
        assertThat(paid.getRefundedAmount()).isEqualByComparingTo("2000");
    }

    @Test
    void reservationCancelRequiresSettlementForCollected() {
        SpacePaymentEntity paid = obligation(SpacePaymentType.RENT, SpacePaymentCategory.ADVANCE, "2000", true);
        SpacePaymentEntity unpaid = obligation(SpacePaymentType.RENT, SpacePaymentCategory.MONTHLY, "4000", false);
        when(paymentRepository.findByOccupancy_Id(occupancy.getId())).thenReturn(List.of(paid, unpaid));

        assertThatThrownBy(() -> settlementService.settleReservationCancellation(
                        spaceId, occupancy, callerId, List.of(), "cancel"))
                .isInstanceOf(BusinessException.class);

        ReservationPaymentSettlementDecision decision = new ReservationPaymentSettlementDecision();
        decision.setPaymentId(paid.getId());
        decision.setAction(PaymentSettlementAction.RETAIN);
        decision.setRetainedAmount(new BigDecimal("2000"));
        decision.setReason("Non-refundable");

        settlementService.settleReservationCancellation(
                spaceId, occupancy, callerId, List.of(decision), "Reservation cancelled");

        assertThat(unpaid.getPaymentStatus()).isEqualTo(SpacePaymentStatus.CANCELLED);
        assertThat(paid.getPaymentStatus()).isEqualTo(SpacePaymentStatus.PAID);
    }

    @Test
    void repeatedFullRefundRejected() {
        SpacePaymentEntity paid = obligation(SpacePaymentType.RENT, SpacePaymentCategory.ADVANCE, "2000", true);
        when(paymentRepository.findByIdAndSpaceId(paid.getId(), spaceId)).thenReturn(Optional.of(paid));
        PaymentSettlementEntity prior = PaymentSettlementEntity.builder()
                .payment(paid)
                .action(PaymentSettlementAction.FULL_REFUND)
                .refundAmount(new BigDecimal("2000"))
                .retainedAmount(BigDecimal.ZERO)
                .build();
        when(settlementRepository.findByPayment_IdInOrderByPerformedAtAsc(any())).thenReturn(List.of(prior));

        SettleSpacePaymentRequest request = new SettleSpacePaymentRequest();
        request.setAction(PaymentSettlementAction.FULL_REFUND);
        request.setReason("again");
        assertThatThrownBy(() -> settlementService.settleCollectedPayment(spaceId, paid.getId(), callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already fully settled");
        verify(paymentRepository, never()).save(eq(paid));
    }

    @Test
    void underReviewWithoutVerifiedCollectionCannotBeRefunded() {
        SpacePaymentEntity underReview =
                obligation(SpacePaymentType.RENT, SpacePaymentCategory.MONTHLY, "5000", false);
        underReview.setPaymentStatus(SpacePaymentStatus.UNDER_REVIEW);
        when(paymentRepository.findByIdAndSpaceId(underReview.getId(), spaceId))
                .thenReturn(Optional.of(underReview));

        SettleSpacePaymentRequest request = new SettleSpacePaymentRequest();
        request.setAction(PaymentSettlementAction.FULL_REFUND);
        request.setReason("Attempted refund");
        assertThatThrownBy(() ->
                        settlementService.settleCollectedPayment(
                                spaceId, underReview.getId(), callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("UNDER_REVIEW");
    }

    @Test
    void remainingRefundAfterPartialMarksRefunded() {
        SpacePaymentEntity paid = obligation(SpacePaymentType.RENT, SpacePaymentCategory.ADVANCE, "5000", true);
        paid.setRefundedAmount(new BigDecimal("2000"));
        when(paymentRepository.findByIdAndSpaceId(paid.getId(), spaceId)).thenReturn(Optional.of(paid));
        PaymentSettlementEntity prior = PaymentSettlementEntity.builder()
                .payment(paid)
                .action(PaymentSettlementAction.PARTIAL_REFUND)
                .refundAmount(new BigDecimal("2000"))
                .retainedAmount(BigDecimal.ZERO)
                .build();
        when(settlementRepository.findByPayment_IdInOrderByPerformedAtAsc(any())).thenReturn(List.of(prior));

        SettleSpacePaymentRequest request = new SettleSpacePaymentRequest();
        request.setAction(PaymentSettlementAction.FULL_REFUND);
        request.setReason("Return remainder");
        settlementService.settleCollectedPayment(spaceId, paid.getId(), callerId, request);

        assertThat(paid.getPaymentStatus()).isEqualTo(SpacePaymentStatus.REFUNDED);
        assertThat(paid.getRefundedAmount()).isEqualByComparingTo("5000");
    }

    @Test
    void refundGreaterThanRemainingRejected() {
        SpacePaymentEntity paid = obligation(SpacePaymentType.RENT, SpacePaymentCategory.ADVANCE, "5000", true);
        when(paymentRepository.findByIdAndSpaceId(paid.getId(), spaceId)).thenReturn(Optional.of(paid));

        SettleSpacePaymentRequest request = new SettleSpacePaymentRequest();
        request.setAction(PaymentSettlementAction.PARTIAL_REFUND);
        request.setRefundAmount(new BigDecimal("6000"));
        request.setReason("Too much");
        assertThatThrownBy(() ->
                        settlementService.settleCollectedPayment(spaceId, paid.getId(), callerId, request))
                .isInstanceOf(BusinessException.class);
    }

    private SpacePaymentEntity obligation(
            SpacePaymentType type, SpacePaymentCategory category, String amount, boolean paid) {
        SpacePaymentEntity payment = SpacePaymentEntity.builder()
                .space(space)
                .member(member)
                .occupancy(occupancy)
                .paymentType(type)
                .paymentCategory(category)
                .title(type + " " + category)
                .amount(new BigDecimal(amount))
                .currencyCode("INR")
                .dueDate(LocalDate.of(2026, 12, 1))
                .month("2026-12")
                .paymentStatus(paid ? SpacePaymentStatus.PAID : SpacePaymentStatus.PENDING)
                .paymentDate(paid ? LocalDate.of(2026, 11, 1) : null)
                .build();
        payment.setId(UUID.randomUUID());
        return payment;
    }
}
