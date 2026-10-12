package com.acomi.acomi_backend.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import com.acomi.acomi_backend.notification.api.dto.response.NotificationResponse;
import com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand;
import com.acomi.acomi_backend.payment.infrastructure.delivery.WhatsAppMessageDeliveryProvider;
import com.acomi.acomi_backend.payment.infrastructure.delivery.WhatsAppPhoneNormalizer;
import com.acomi.acomi_backend.payment.infrastructure.delivery.WhatsAppReminderProperties;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.PaymentReminderDeliveryEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.PaymentReminderDeliveryRepository;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
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
import org.springframework.http.HttpStatus;
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
                new WhatsAppPhoneNormalizer(new WhatsAppReminderProperties()),
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
    void processSpace_publishesMemberAndOwnerOverdueAlertsWhenWhatsAppFails() {
        SpaceEntity space = activeSpace();
        OverduePaymentResponse eligible = overdueRow(paymentId, "6000", 3);
        UUID memberUserId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UserEntity memberUser = new UserEntity();
        memberUser.setId(memberUserId);
        UserEntity ownerUser = new UserEntity();
        ownerUser.setId(ownerUserId);
        SpacePaymentEntity payment = paymentEntity(space, eligible);
        payment.setId(paymentId);
        payment.getMember().setUser(memberUser);
        com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity owner =
                com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity.builder()
                        .user(ownerUser)
                        .role(com.acomi.acomi_backend.member.domain.model.MembershipRole.OWNER)
                        .status(com.acomi.acomi_backend.member.domain.model.MembershipStatus.ACTIVE)
                        .build();

        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(eligibilityService.findObligationsNeedingReminder(spaceId, callerId))
                .thenReturn(List.of(eligible));
        when(paymentRepository.findByIdAndSpaceId(paymentId, spaceId)).thenReturn(Optional.of(payment));
        when(membershipRepository.findBySpaceIdAndStatus(eq(spaceId), any())).thenReturn(List.of(owner));
        when(deliveryRepository.findByPaymentIdAndReminderTypeAndBusinessDateAndChannel(
                        eq(paymentId), any(), any(), eq(ReminderChannel.WHATSAPP)))
                .thenReturn(Optional.empty());
        when(deliveryRepository.saveAndFlush(any())).thenAnswer(inv -> {
            PaymentReminderDeliveryEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });
        when(deliveryRepository.findById(any())).thenReturn(Optional.of(pendingDelivery(UUID.randomUUID())));
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(whatsAppProvider.deliver(any()))
                .thenReturn(MessageDeliveryProvider.MessageDeliveryResult.unavailable(
                        "WHATSAPP_PROVIDER_NOT_CONFIGURED"));

        service.processSpace(spaceId, callerId);
        service.processSpace(spaceId, callerId);

        ArgumentCaptor<PublishNotificationCommand> commands =
                ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService, org.mockito.Mockito.atLeast(2)).publish(commands.capture());
        PublishNotificationCommand member = commands.getAllValues().stream()
                .filter(command -> memberUserId.equals(command.getUserId()))
                .findFirst()
                .orElseThrow();
        PublishNotificationCommand ownerNotice = commands.getAllValues().stream()
                .filter(command -> ownerUserId.equals(command.getUserId()))
                .findFirst()
                .orElseThrow();
        assertThat(member.getTitle()).isEqualTo("Payment Overdue");
        assertThat(member.getNotificationType()).isEqualTo(com.acomi.acomi_backend.notification.domain.model.NotificationType.PAYMENT_REMINDER_SENT);
        assertThat(member.getMessage()).contains("₹6,000").contains("ABC PG").contains("3 days");
        assertThat(member.getDedupeKey()).isEqualTo("OVERDUE:MEMBER:DAY3:" + paymentId + ":" + memberUserId);
        assertThat(ownerNotice.getNotificationType())
                .isEqualTo(com.acomi.acomi_backend.notification.domain.model.NotificationType.PAYMENT_OVERDUE);
        assertThat(ownerNotice.getDedupeKey()).isEqualTo("OVERDUE:OWNER:" + paymentId + ":" + ownerUserId);
        assertThat(commands.getAllValues().stream()
                .filter(command -> member.getDedupeKey().equals(command.getDedupeKey()))
                .count()).isEqualTo(2);
    }

    @Test
    void prepareOwnerReminder_recordsInAppNoticeAndReturnsWhatsAppLinkWithoutChangingPayment() {
        SpaceEntity space = activeSpace();
        space.setName("Sunrise PG");
        OverduePaymentResponse eligible = OverduePaymentResponse.builder()
                .paymentId(paymentId)
                .spaceId(spaceId)
                .spaceName("Sunrise PG")
                .memberId(memberId)
                .memberName("Rahul Kumar")
                .memberMobile("9999999991")
                .paymentType(SpacePaymentType.RENT)
                .title("Rent – October 2026")
                .month("2026-10")
                .dueDate(LocalDate.of(2026, 10, 1))
                .outstandingAmount(new BigDecimal("6000"))
                .currencyCode("INR")
                .paymentStatus(SpacePaymentStatus.PENDING)
                .daysOverdue(10)
                .reminderEligible(true)
                .build();
        UserEntity user = UserEntity.builder().build();
        user.setId(UUID.randomUUID());
        SpacePaymentEntity payment = paymentEntity(space, eligible);
        payment.setTargetLabel("Unit 101 · Room A");
        payment.getMember().setUser(user);
        payment.setId(paymentId);

        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(eligibilityService.findObligationsNeedingReminderInternal(spaceId)).thenReturn(List.of(eligible));
        when(paymentRepository.findByIdAndSpaceId(paymentId, spaceId)).thenReturn(Optional.of(payment));
        when(notificationService.publish(any())).thenReturn(NotificationResponse.builder()
                .notificationId(UUID.randomUUID())
                .userId(user.getId())
                .entityId(paymentId)
                .actionRoute("PaymentDetail")
                .createdAt(java.time.LocalDateTime.now())
                .build());

        PaymentReminderDeliveryResponse response = service.prepareOwnerReminder(spaceId, paymentId, callerId);

        assertThat(payment.getPaymentStatus()).isEqualTo(SpacePaymentStatus.PENDING);
        assertThat(response.isInAppRecorded()).isTrue();
        assertThat(response.getInAppFailureCode()).isNull();
        assertThat(response.getInternationalPhone()).isEqualTo("919999999991");
        assertThat(response.getWhatsAppUrl()).startsWith("https://wa.me/919999999991?text=");
        assertThat(response.getWhatsAppUrl()).contains("Rahul%20Kumar");
        assertThat(response.getMessageText()).contains("Rent");
        assertThat(response.getMessageText()).contains("₹6,000");
        assertThat(response.getMessageText()).contains("October 2026");
        assertThat(response.getMessageText()).contains("Sunrise PG");
        assertThat(response.getMessageText()).contains("Room A");
        assertThat(response.getMessageText()).contains("Unit 101");
        assertThat(response.getMessageText()).contains("1 October 2026");
        assertThat(response.getMessageText()).contains("Overdue");
        assertThat(response.getDeliveryStatus()).isEqualTo(ReminderDeliveryStatus.PENDING);
        verify(whatsAppProvider, never()).deliver(any());
        verify(paymentRepository, never()).save(any());

        ArgumentCaptor<PublishNotificationCommand> command = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService).publish(command.capture());
        assertThat(command.getValue().getUserId()).isEqualTo(user.getId());
        assertThat(command.getValue().getEntityId()).isEqualTo(paymentId);
        assertThat(command.getValue().getActionRoute()).isEqualTo("PaymentDetail");
        assertThat(command.getValue().getMessage()).contains("Rent").contains("₹6,000").contains("Sunrise PG");
        assertThat(command.getValue().getDeliveryChannels()).containsExactly("IN_APP");
    }

    @Test
    void prepareOwnerReminder_keepsWhatsAppLinkWhenNotificationFails() {
        SpaceEntity space = activeSpace();
        OverduePaymentResponse eligible = overdueRow(paymentId, "6000", 2);
        UserEntity user = UserEntity.builder().build();
        user.setId(UUID.randomUUID());
        SpacePaymentEntity payment = paymentEntity(space, eligible);
        payment.getMember().setUser(user);

        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(eligibilityService.findObligationsNeedingReminderInternal(spaceId)).thenReturn(List.of(eligible));
        when(paymentRepository.findByIdAndSpaceId(paymentId, spaceId)).thenReturn(Optional.of(payment));
        when(notificationService.publish(any())).thenThrow(new IllegalStateException("db"));

        PaymentReminderDeliveryResponse response = service.prepareOwnerReminder(spaceId, paymentId, callerId);

        assertThat(response.isInAppRecorded()).isFalse();
        assertThat(response.getInAppFailureCode()).isEqualTo("NOTIFICATION_FAILED");
        assertThat(response.isRetryable()).isTrue();
        assertThat(response.getWhatsAppUrl()).startsWith("https://wa.me/919876543210?text=");
        assertThat(payment.getPaymentStatus()).isEqualTo(SpacePaymentStatus.PENDING);
        verify(whatsAppProvider, never()).deliver(any());
    }

    @Test
    void prepareOwnerReminder_reportsMissingAccountAndInvalidPhone() {
        SpaceEntity space = activeSpace();
        OverduePaymentResponse eligible = OverduePaymentResponse.builder()
                .paymentId(paymentId)
                .spaceId(spaceId)
                .memberId(memberId)
                .memberName("Rahul")
                .memberMobile("123")
                .paymentType(SpacePaymentType.RENT)
                .title("Rent")
                .month("2026-10")
                .dueDate(businessDate.minusDays(1))
                .outstandingAmount(new BigDecimal("1000"))
                .currencyCode("INR")
                .daysOverdue(1)
                .build();
        SpacePaymentEntity payment = paymentEntity(space, eligible);

        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(eligibilityService.findObligationsNeedingReminderInternal(spaceId)).thenReturn(List.of(eligible));
        when(paymentRepository.findByIdAndSpaceId(paymentId, spaceId)).thenReturn(Optional.of(payment));

        PaymentReminderDeliveryResponse response = service.prepareOwnerReminder(spaceId, paymentId, callerId);

        assertThat(response.getInAppFailureCode()).isEqualTo("MEMBER_NOT_ON_ACOMI");
        assertThat(response.isInAppRecorded()).isFalse();
        assertThat(response.getWhatsAppUrl()).isNull();
        assertThat(response.getFailureCode()).isEqualTo("INVALID_RECIPIENT");
        assertThat(response.getMessageText()).isNotBlank();
        verify(notificationService, never()).publish(any());
        verify(whatsAppProvider, never()).deliver(any());
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
    void processPayment_forbiddenForNonManagers() {
        doThrow(new BusinessException("Only OWNER or MANAGER can review payments", HttpStatus.FORBIDDEN))
                .when(accessService)
                .requireManagePayments(spaceId, callerId);

        assertThatThrownBy(() -> service.processPayment(spaceId, paymentId, callerId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OWNER or MANAGER");
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
