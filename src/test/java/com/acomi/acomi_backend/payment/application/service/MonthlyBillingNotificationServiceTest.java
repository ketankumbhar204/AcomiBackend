package com.acomi.acomi_backend.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.notification.application.service.DeviceTokenService;
import com.acomi.acomi_backend.notification.application.service.NotificationService;
import com.acomi.acomi_backend.notification.application.service.PushNotificationService;
import com.acomi.acomi_backend.notification.application.service.PushNotificationService.TrackedPushResult;
import com.acomi.acomi_backend.notification.infrastructure.persistence.entity.UserDeviceTokenEntity;
import com.acomi.acomi_backend.payment.domain.model.MonthlyBillingNotificationStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.MonthlyBillingNotificationEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.MonthlyBillingNotificationRepository;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonthlyBillingNotificationServiceTest {

    @Mock
    private MonthlyBillingNotificationRepository notificationRepository;
    @Mock
    private SpacePaymentRepository paymentRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private PushNotificationService pushNotificationService;
    @Mock
    private DeviceTokenService deviceTokenService;

    @InjectMocks
    private MonthlyBillingNotificationService service;

    @Test
    void messageNamesTheSpaceMonthAmountAndDueDate() {
        SpaceEntity space = SpaceEntity.builder().name("Sunrise PG").build();
        SpacePaymentEntity payment = SpacePaymentEntity.builder()
                .space(space)
                .amount(new BigDecimal("6000"))
                .month("2026-12")
                .dueDate(LocalDate.of(2026, 12, 1))
                .build();

        assertThat(MonthlyBillingNotificationService.message(payment))
                .isEqualTo("Your December 2026 rent of ₹6,000 has been generated for Sunrise PG. "
                        + "Due date: 1 December 2026. Open ACOMI to view payment details.");
    }

    @Test
    void firebaseFailureKeepsTheOutboxPendingAndDoesNotSaveThePayment() {
        UUID paymentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MonthlyBillingNotificationEntity row = pending(paymentId, userId);
        SpacePaymentEntity payment = openPayment(paymentId);
        when(notificationRepository.findById(row.getId())).thenReturn(Optional.of(row));
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(deviceTokenService.listActiveForUser(userId))
                .thenReturn(List.of(UserDeviceTokenEntity.builder().fcmToken("token").build()));
        when(pushNotificationService.deliverTracked(any(), any(), any(), any()))
                .thenReturn(TrackedPushResult.blocked("FIREBASE_NOT_INITIALIZED", "down"));

        service.deliverOne(row.getId());

        verify(paymentRepository, never()).save(any());
        assertThat(row.getStatus()).isEqualTo(MonthlyBillingNotificationStatus.PENDING);
        assertThat(row.getLastError()).isEqualTo("FIREBASE_NOT_INITIALIZED");
    }

    @Test
    void acceptedSendIsNotRetried() {
        UUID paymentId = UUID.randomUUID();
        MonthlyBillingNotificationEntity row = pending(paymentId, UUID.randomUUID());
        row.setStatus(MonthlyBillingNotificationStatus.FCM_ACCEPTED);

        when(notificationRepository.findById(row.getId())).thenReturn(Optional.of(row));

        service.deliverOne(row.getId());

        verify(pushNotificationService, never()).deliverTracked(any(), any(), any(), any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void enqueueDoesNotDuplicateAnExistingEvent() {
        SpacePaymentEntity payment = openPayment(UUID.randomUUID());
        MemberEntity member = MemberEntity.builder().fullName("A").build();
        member.setId(UUID.randomUUID());
        payment.setMember(member);
        when(notificationRepository.findByPaymentIdAndEventType(any(), any()))
                .thenReturn(Optional.of(MonthlyBillingNotificationEntity.builder().build()));

        assertThat(service.enqueue(payment, java.time.YearMonth.of(2026, 12))).isFalse();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void enqueueTargetsTheCustomerAccount() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        MemberEntity member = MemberEntity.builder().fullName("A").build();
        member.setId(UUID.randomUUID());
        member.setUser(user);
        SpaceEntity space = SpaceEntity.builder().name("Sunrise PG").build();
        space.setId(UUID.randomUUID());
        SpacePaymentEntity payment = openPayment(UUID.randomUUID());
        payment.setSpace(space);
        payment.setMember(member);
        when(notificationRepository.findByPaymentIdAndEventType(any(), any())).thenReturn(Optional.empty());
        when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.enqueue(payment, java.time.YearMonth.of(2026, 12))).isTrue();

        ArgumentCaptor<MonthlyBillingNotificationEntity> captor =
                ArgumentCaptor.forClass(MonthlyBillingNotificationEntity.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipientUserId()).isEqualTo(user.getId());
        assertThat(captor.getValue().getStatus()).isEqualTo(MonthlyBillingNotificationStatus.PENDING);
    }

    private static MonthlyBillingNotificationEntity pending(UUID paymentId, UUID userId) {
        MonthlyBillingNotificationEntity row = MonthlyBillingNotificationEntity.builder()
                .paymentId(paymentId)
                .recipientUserId(userId)
                .recipientMemberId(UUID.randomUUID())
                .spaceId(UUID.randomUUID())
                .eventType(MonthlyBillingNotificationEntity.EVENT_MONTHLY_RENT_GENERATED)
                .billingMonth(LocalDate.of(2026, 12, 1))
                .status(MonthlyBillingNotificationStatus.PENDING)
                .attemptCount(0)
                .build();
        row.setId(UUID.randomUUID());
        return row;
    }

    private static SpacePaymentEntity openPayment(UUID id) {
        SpaceEntity space = SpaceEntity.builder().name("Sunrise PG").build();
        space.setId(UUID.randomUUID());
        SpacePaymentEntity payment = SpacePaymentEntity.builder()
                .space(space)
                .amount(new BigDecimal("6000"))
                .month("2026-12")
                .dueDate(LocalDate.of(2026, 12, 1))
                .paymentStatus(SpacePaymentStatus.PENDING)
                .build();
        payment.setId(id);
        return payment;
    }
}
