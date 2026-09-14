package com.acomi.acomi_backend.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.notification.application.service.NotificationService;
import com.acomi.acomi_backend.payment.api.dto.response.OverduePaymentResponse;
import com.acomi.acomi_backend.payment.api.dto.response.PaymentReminderDeliveryResponse;
import com.acomi.acomi_backend.payment.api.dto.response.PaymentReminderProcessResultResponse;
import com.acomi.acomi_backend.payment.application.port.out.MessageDeliveryProvider;
import com.acomi.acomi_backend.payment.domain.model.PaymentReminderType;
import com.acomi.acomi_backend.payment.domain.model.PaymentSettlementStatus;
import com.acomi.acomi_backend.payment.domain.model.ReminderChannel;
import com.acomi.acomi_backend.payment.domain.model.ReminderDeliveryStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import com.acomi.acomi_backend.payment.infrastructure.delivery.WhatsAppMessageDeliveryProvider;
import com.acomi.acomi_backend.payment.infrastructure.delivery.WhatsAppReminderProperties;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.PaymentReminderDeliveryEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.PaymentReminderDeliveryRepository;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentReminderServiceTest {

    @Mock
    private PaymentReminderEligibilityService eligibilityService;
    @Mock
    private SpacePaymentAccessService accessService;
    @Mock
    private SpaceRepository spaceRepository;
    @Mock
    private SpacePaymentRepository paymentRepository;
    @Mock
    private PaymentReminderDeliveryRepository deliveryRepository;
    @Mock
    private WhatsAppMessageDeliveryProvider whatsAppProvider;
    @Mock
    private WhatsAppReminderProperties whatsAppProperties;
    @Mock
    private SpacePaymentTimelineService timelineService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private SpaceMembershipRepository membershipRepository;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private TransactionStatus transactionStatus;

    private PaymentReminderService service;

    private final UUID spaceId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();
    private final LocalDate businessDate = LocalDate.of(2026, 9, 4);

    @BeforeEach
    void setUp() {
        service = new PaymentReminderService(
                eligibilityService,
                accessService,
                spaceRepository,
                paymentRepository,
                deliveryRepository,
                whatsAppProvider,
                whatsAppProperties,
                timelineService,
                notificationService,
                membershipRepository,
                transactionTemplate);

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });

        when(whatsAppProvider.getMode()).thenReturn("logging");
        when(whatsAppProvider.isConfigured()).thenReturn(true);
        when(whatsAppProvider.channel()).thenReturn(ReminderChannel.WHATSAPP);
        when(whatsAppProperties.getMaxTransientAttempts()).thenReturn(3);
        when(membershipRepository.findBySpaceIdAndStatus(any(), any())).thenReturn(List.of());
    }

    @Test
    void processSpace_sendsEligibleReminderUsingOutstandingAmount() {
        SpaceEntity space = activeSpace();
        OverduePaymentResponse eligible = overdueRow(paymentId, "10000", 3);
        PaymentReminderDeliveryEntity pending = pendingDelivery(null);

        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(eligibilityService.findObligationsNeedingReminder(spaceId, callerId))
                .thenReturn(List.of(eligible));
        when(deliveryRepository.findByPaymentIdAndReminderTypeAndBusinessDateAndChannel(
                        eq(paymentId),
                        eq(PaymentReminderType.RENT_PAYMENT_OVERDUE),
                        eq(businessDate),
                        eq(ReminderChannel.WHATSAPP)))
                .thenReturn(Optional.empty());
        when(paymentRepository.findByIdAndSpaceId(paymentId, spaceId))
                .thenReturn(Optional.of(paymentEntity(space, eligible)));
        when(deliveryRepository.saveAndFlush(any())).thenAnswer(inv -> {
            PaymentReminderDeliveryEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            pending.setId(e.getId());
            return e;
        });
        when(deliveryRepository.findById(any())).thenAnswer(inv -> {
            pending.setId(inv.getArgument(0));
            return Optional.of(pending);
        });
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(whatsAppProvider.deliver(any()))
                .thenReturn(MessageDeliveryProvider.MessageDeliveryResult.sent("log-1"));

        // Freeze business date via space timezone Asia/Kolkata — use fixed due math from eligibility DTO
        // Override eligibility business date by stubbing space timezone and MealPoll — simpler: stub
        // process uses PaymentDueStatusCalculator.businessDate(space) which is "today" in space TZ.
        // For unit test, set eligibility list and force claim with today's date from calculator.
        // Instead, capture message after making businessDate match by stubbing delivery lookup loosely.

        when(deliveryRepository.findByPaymentIdAndReminderTypeAndBusinessDateAndChannel(
                        eq(paymentId), any(), any(), eq(ReminderChannel.WHATSAPP)))
                .thenReturn(Optional.empty());

        PaymentReminderProcessResultResponse result = service.processSpace(spaceId, callerId);

        ArgumentCaptor<com.acomi.acomi_backend.payment.application.dto.PaymentReminderMessage> msg =
                ArgumentCaptor.forClass(
                        com.acomi.acomi_backend.payment.application.dto.PaymentReminderMessage.class);
        verify(whatsAppProvider).deliver(msg.capture());
        assertThat(msg.getValue().getOutstandingAmount()).isEqualByComparingTo("10000");
        assertThat(msg.getValue().getDaysOverdue()).isEqualTo(3);
        assertThat(result.getDeliverySuccesses()).isEqualTo(1);
        assertThat(result.getRemindersCreated()).isEqualTo(1);
        verify(accessService).requireManagePayments(spaceId, callerId);
    }

    @Test
    void duplicateSameDay_isSkipped() {
        SpaceEntity space = activeSpace();
        OverduePaymentResponse eligible = overdueRow(paymentId, "10000", 2);
        PaymentReminderDeliveryEntity sent = PaymentReminderDeliveryEntity.builder()
                .spaceId(spaceId)
                .paymentId(paymentId)
                .recipientMemberId(memberId)
                .reminderType(PaymentReminderType.RENT_PAYMENT_OVERDUE)
                .channel(ReminderChannel.WHATSAPP)
                .businessDate(LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")))
                .deliveryStatus(ReminderDeliveryStatus.SENT)
                .outstandingAmount(new BigDecimal("10000"))
                .attemptCount(1)
                .build();
        sent.setId(UUID.randomUUID());

        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(eligibilityService.findObligationsNeedingReminder(spaceId, callerId))
                .thenReturn(List.of(eligible));
        when(deliveryRepository.findByPaymentIdAndReminderTypeAndBusinessDateAndChannel(
                        eq(paymentId), any(), any(), eq(ReminderChannel.WHATSAPP)))
                .thenReturn(Optional.of(sent));

        PaymentReminderProcessResultResponse result = service.processSpace(spaceId, callerId);

        assertThat(result.getRemindersSkippedDuplicate()).isEqualTo(1);
        assertThat(result.getDeliverySuccesses()).isZero();
        verify(whatsAppProvider, never()).deliver(any());
    }

    @Test
    void providerFailure_doesNotMarkSent_andLeavesPaymentUntouched() {
        SpaceEntity space = activeSpace();
        OverduePaymentResponse eligible = overdueRow(paymentId, "10000", 1);
        AtomicReference<PaymentReminderDeliveryEntity> stored = new AtomicReference<>();

        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(eligibilityService.findObligationsNeedingReminder(spaceId, callerId))
                .thenReturn(List.of(eligible));
        when(deliveryRepository.findByPaymentIdAndReminderTypeAndBusinessDateAndChannel(
                        eq(paymentId), any(), any(), eq(ReminderChannel.WHATSAPP)))
                .thenReturn(Optional.empty());
        when(paymentRepository.findByIdAndSpaceId(paymentId, spaceId))
                .thenReturn(Optional.of(paymentEntity(space, eligible)));
        when(deliveryRepository.saveAndFlush(any())).thenAnswer(inv -> {
            PaymentReminderDeliveryEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            stored.set(e);
            return e;
        });
        when(deliveryRepository.findById(any())).thenAnswer(inv -> Optional.of(stored.get()));
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(whatsAppProvider.deliver(any()))
                .thenReturn(MessageDeliveryProvider.MessageDeliveryResult.unavailable(
                        "WHATSAPP_PROVIDER_NOT_CONFIGURED"));

        PaymentReminderProcessResultResponse result = service.processSpace(spaceId, callerId);

        assertThat(result.getDeliveryFailures()).isEqualTo(1);
        assertThat(stored.get().getDeliveryStatus()).isEqualTo(ReminderDeliveryStatus.FAILED);
        // Payment status never written via paymentRepository.save
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processPayment_rejectsWhenNotEligible() {
        SpaceEntity space = activeSpace();
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(eligibilityService.findObligationsNeedingReminderInternal(spaceId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.processPayment(spaceId, paymentId, callerId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not reminder-eligible");
        verify(whatsAppProvider, never()).deliver(any());
    }

    @Test
    void failedDelivery_canBeRetried() {
        SpaceEntity space = activeSpace();
        OverduePaymentResponse eligible = overdueRow(paymentId, "5000", 2);
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        PaymentReminderDeliveryEntity failed = PaymentReminderDeliveryEntity.builder()
                .spaceId(spaceId)
                .paymentId(paymentId)
                .recipientMemberId(memberId)
                .reminderType(PaymentReminderType.RENT_PAYMENT_OVERDUE)
                .channel(ReminderChannel.WHATSAPP)
                .businessDate(today)
                .deliveryStatus(ReminderDeliveryStatus.FAILED)
                .outstandingAmount(new BigDecimal("5000"))
                .failureReason("PROVIDER_TIMEOUT: Provider timeout or network failure")
                .attemptCount(1)
                .build();
        failed.setId(UUID.randomUUID());

        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(eligibilityService.findObligationsNeedingReminderInternal(spaceId))
                .thenReturn(List.of(eligible));
        when(deliveryRepository.findByPaymentIdAndReminderTypeAndBusinessDateAndChannel(
                        eq(paymentId), any(), any(), eq(ReminderChannel.WHATSAPP)))
                .thenReturn(Optional.of(failed));
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(deliveryRepository.findById(failed.getId())).thenReturn(Optional.of(failed));
        when(paymentRepository.findByIdAndSpaceId(paymentId, spaceId))
                .thenReturn(Optional.of(paymentEntity(space, eligible)));
        when(whatsAppProvider.deliver(any()))
                .thenReturn(MessageDeliveryProvider.MessageDeliveryResult.sent("log-retry"));

        PaymentReminderDeliveryResponse response =
                service.processPayment(spaceId, paymentId, callerId);

        assertThat(response.getDeliveryStatus()).isEqualTo(ReminderDeliveryStatus.SENT);
        assertThat(failed.getAttemptCount()).isEqualTo(2);
    }

    @Test
    void permanentFailure_isNotRetriedSameDay() {
        SpaceEntity space = activeSpace();
        OverduePaymentResponse eligible = overdueRow(paymentId, "5000", 2);
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        PaymentReminderDeliveryEntity failed = PaymentReminderDeliveryEntity.builder()
                .spaceId(spaceId)
                .paymentId(paymentId)
                .recipientMemberId(memberId)
                .reminderType(PaymentReminderType.RENT_PAYMENT_OVERDUE)
                .channel(ReminderChannel.WHATSAPP)
                .businessDate(today)
                .deliveryStatus(ReminderDeliveryStatus.FAILED)
                .outstandingAmount(new BigDecimal("5000"))
                .failureReason("INVALID_RECIPIENT: Recipient mobile number is missing or invalid")
                .attemptCount(1)
                .build();
        failed.setId(UUID.randomUUID());

        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(eligibilityService.findObligationsNeedingReminderInternal(spaceId))
                .thenReturn(List.of(eligible));
        when(deliveryRepository.findByPaymentIdAndReminderTypeAndBusinessDateAndChannel(
                        eq(paymentId), any(), any(), eq(ReminderChannel.WHATSAPP)))
                .thenReturn(Optional.of(failed));

        PaymentReminderDeliveryResponse response =
                service.processPayment(spaceId, paymentId, callerId);

        assertThat(response.getDeliveryStatus()).isEqualTo(ReminderDeliveryStatus.FAILED);
        assertThat(response.getFailureCode()).isEqualTo("INVALID_RECIPIENT");
        verify(whatsAppProvider, never()).deliver(any());
    }

    private SpaceEntity activeSpace() {
        SpaceEntity space = new SpaceEntity();
        space.setId(spaceId);
        space.setName("ABC PG");
        space.setActive(true);
        space.setTimezone("Asia/Kolkata");
        return space;
    }

    private OverduePaymentResponse overdueRow(UUID id, String outstanding, int days) {
        return OverduePaymentResponse.builder()
                .paymentId(id)
                .spaceId(spaceId)
                .spaceName("ABC PG")
                .memberId(memberId)
                .memberName("Rahul")
                .memberMobile("9876543210")
                .paymentType(SpacePaymentType.RENT)
                .title("Rent Sep 2026")
                .month("2026-09")
                .dueDate(businessDate.minusDays(days))
                .totalAmount(new BigDecimal(outstanding))
                .paidAmount(BigDecimal.ZERO)
                .outstandingAmount(new BigDecimal(outstanding))
                .currencyCode("INR")
                .paymentStatus(SpacePaymentStatus.PENDING)
                .settlementStatus(PaymentSettlementStatus.UNPAID)
                .overdue(true)
                .daysOverdue(days)
                .reminderEligible(true)
                .build();
    }

    private SpacePaymentEntity paymentEntity(SpaceEntity space, OverduePaymentResponse row) {
        return SpacePaymentEntity.builder()
                .space(space)
                .member(MemberEntity.builder()
                        .isActive(true)
                        .fullName(row.getMemberName())
                        .mobileNumber(row.getMemberMobile())
                        .build())
                .paymentType(row.getPaymentType())
                .title(row.getTitle())
                .amount(row.getOutstandingAmount())
                .currencyCode(row.getCurrencyCode())
                .dueDate(row.getDueDate())
                .paymentStatus(SpacePaymentStatus.PENDING)
                .build();
    }

    private PaymentReminderDeliveryEntity pendingDelivery(UUID id) {
        PaymentReminderDeliveryEntity e = PaymentReminderDeliveryEntity.builder()
                .spaceId(spaceId)
                .paymentId(paymentId)
                .recipientMemberId(memberId)
                .reminderType(PaymentReminderType.RENT_PAYMENT_OVERDUE)
                .channel(ReminderChannel.WHATSAPP)
                .businessDate(businessDate)
                .deliveryStatus(ReminderDeliveryStatus.PENDING)
                .outstandingAmount(new BigDecimal("10000"))
                .attemptCount(1)
                .build();
        e.setId(id);
        return e;
    }
}
