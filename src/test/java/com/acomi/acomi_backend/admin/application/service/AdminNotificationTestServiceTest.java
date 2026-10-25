package com.acomi.acomi_backend.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.admin.api.dto.request.AdminNotificationTestRequest;
import com.acomi.acomi_backend.admin.api.dto.response.AdminNotificationTestPageResponse.AttemptItem;
import com.acomi.acomi_backend.admin.application.AdminSecurityAudit;
import com.acomi.acomi_backend.admin.application.support.AdminNotificationTestRateLimit;
import com.acomi.acomi_backend.admin.application.support.NotificationTestCatalog;
import com.acomi.acomi_backend.admin.infrastructure.persistence.entity.AdminNotificationTestAttemptEntity;
import com.acomi.acomi_backend.admin.infrastructure.persistence.repository.AdminNotificationTestAttemptRepository;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.RateLimitedException;
import com.acomi.acomi_backend.notification.api.dto.response.NotificationResponse;
import com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand;
import com.acomi.acomi_backend.notification.application.service.DeviceTokenService;
import com.acomi.acomi_backend.notification.application.service.NotificationService;
import com.acomi.acomi_backend.notification.application.service.PushNotificationService;
import com.acomi.acomi_backend.notification.application.service.PushNotificationService.DevicePushResult;
import com.acomi.acomi_backend.notification.application.service.PushNotificationService.TrackedPushResult;
import com.acomi.acomi_backend.notification.domain.model.DevicePlatform;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.notification.infrastructure.firebase.FirebasePushStatus;
import com.acomi.acomi_backend.notification.infrastructure.persistence.entity.UserDeviceTokenEntity;
import com.acomi.acomi_backend.notification.infrastructure.persistence.repository.UserDeviceTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminNotificationTestServiceTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private PushNotificationService pushNotificationService;
    @Mock
    private DeviceTokenService deviceTokenService;
    @Mock
    private UserDeviceTokenRepository deviceTokenRepository;
    @Mock
    private AdminNotificationTestAttemptRepository attemptRepository;
    @Mock
    private AdminSecurityAudit securityAudit;
    private AdminNotificationTestService service;
    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AdminNotificationTestService(
                notificationService,
                pushNotificationService,
                deviceTokenService,
                deviceTokenRepository,
                attemptRepository,
                new FirebasePushStatus(false, "NONE", false, "UNKNOWN", false, "PUSH_DISABLED"),
                new AdminNotificationTestRateLimit(Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC)),
                securityAudit);
        when(attemptRepository.save(any())).thenAnswer(invocation -> {
            AdminNotificationTestAttemptEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });
    }

    @Test
    void catalogIncludesEveryNotificationType() {
        assertThat(NotificationTestCatalog.all())
                .extracting(entry -> entry.type())
                .containsExactlyInAnyOrder(NotificationType.values());
        assertThat(NotificationTestCatalog.all()).allMatch(entry -> entry.safeToTest());
        assertThat(NotificationTestCatalog.all())
                .allMatch(entry -> !entry.channels().contains("BROWSER"));
    }

    @Test
    void rejectsARequestThatIsNotATest() {
        AdminNotificationTestRequest request = request(NotificationType.PAYMENT_REMINDER_SENT);
        request.setTestMode(false);

        assertThatThrownBy(() -> service.send(adminId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("testMode");
        verify(notificationService, never()).publish(any());
        verify(pushNotificationService, never()).deliverTracked(anyString(), anyString(), anyMap(), anyList());
    }

    @Test
    void paymentReminderTestDoesNotTouchPaymentWorkflows() {
        when(deviceTokenService.listActiveForUser(adminId)).thenReturn(List.of(device(adminId)));
        when(notificationService.publish(any())).thenReturn(NotificationResponse.builder()
                .notificationId(UUID.randomUUID())
                .build());
        when(pushNotificationService.deliverTracked(anyString(), anyString(), anyMap(), anyList()))
                .thenReturn(new TrackedPushResult(
                        "FCM_ACCEPTED", null, "accepted", 1, 1, 0, true, List.of(acceptedDevice())));

        AttemptItem result = service.send(adminId, request(NotificationType.PAYMENT_REMINDER_SENT));

        assertThat(result.getStatus()).isEqualTo("FCM_ACCEPTED");
        assertThat(result.isClientReceiptObserved()).isFalse();
        ArgumentCaptor<PublishNotificationCommand> command = ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService).publish(command.capture());
        assertThat(command.getValue().getUserId()).isEqualTo(adminId);
        assertThat(command.getValue().getNotificationType()).isEqualTo(NotificationType.PAYMENT_REMINDER_SENT);
        assertThat(command.getValue().isSuppressAsyncPush()).isTrue();
        assertThat(command.getValue().getEntityId()).isNull();
        assertThat(command.getValue().getSpaceId()).isNull();
        assertThat(command.getValue().getActionRoute()).isEqualTo("NotificationTest");
    }

    @Test
    void noRegistrationIsBlockedAndDoesNotClaimFcmAcceptance() {
        when(deviceTokenService.listActiveForUser(adminId)).thenReturn(List.of());
        when(notificationService.publish(any())).thenReturn(NotificationResponse.builder()
                .notificationId(UUID.randomUUID())
                .build());
        when(pushNotificationService.deliverTracked(anyString(), anyString(), anyMap(), anyList()))
                .thenReturn(TrackedPushResult.blocked(
                        "NO_REGISTRATION", "This admin account has no active mobile registration."));

        AttemptItem result = service.send(adminId, request(NotificationType.PAYMENT_APPROVED));

        assertThat(result.getStatus()).isEqualTo("BLOCKED");
        assertThat(result.getErrorCode()).isEqualTo("NO_REGISTRATION");
        assertThat(result.isFcmAttempted()).isFalse();
        assertThat(result.isFcmAccepted()).isFalse();
        assertThat(result.isNotificationPersisted()).isTrue();
    }

    @Test
    void pushDisabledIsBlocked() {
        when(deviceTokenService.listActiveForUser(adminId)).thenReturn(List.of(device(adminId)));
        when(notificationService.publish(any())).thenReturn(NotificationResponse.builder()
                .notificationId(UUID.randomUUID())
                .build());
        when(pushNotificationService.deliverTracked(anyString(), anyString(), anyMap(), anyList()))
                .thenReturn(TrackedPushResult.blocked("PUSH_DISABLED", "Push is disabled"));

        AttemptItem result = service.send(adminId, request(NotificationType.COMPLAINT_CREATED));

        assertThat(result.getStatus()).isEqualTo("BLOCKED");
        assertThat(result.getErrorCode()).isEqualTo("PUSH_DISABLED");
        assertThat(result.isFcmAccepted()).isFalse();
    }

    @Test
    void registrationOwnedBySomeoneElseSendsNothing() {
        UUID other = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        UserDeviceTokenEntity foreign = device(other);
        foreign.setId(tokenId);
        when(deviceTokenRepository.findById(tokenId)).thenReturn(Optional.of(foreign));
        AdminNotificationTestRequest request = request(NotificationType.CONTACT_ENQUIRY);
        request.setDeviceTokenIds(List.of(tokenId));

        AttemptItem result = service.send(adminId, request);

        assertThat(result.getStatus()).isEqualTo("BLOCKED");
        assertThat(result.getErrorCode()).isEqualTo("REGISTRATION_OTHER_USER");
        verify(notificationService, never()).publish(any());
        verify(pushNotificationService, never()).deliverTracked(anyString(), anyString(), anyMap(), anyList());
    }

    @Test
    void partialFailureKeepsBothCounts() {
        when(deviceTokenService.listActiveForUser(adminId)).thenReturn(List.of(device(adminId), device(adminId)));
        when(notificationService.publish(any())).thenReturn(NotificationResponse.builder()
                .notificationId(UUID.randomUUID())
                .build());
        when(pushNotificationService.deliverTracked(anyString(), anyString(), anyMap(), anyList()))
                .thenReturn(new TrackedPushResult(
                        "PARTIAL_SUCCESS",
                        "UNREGISTERED",
                        "FCM did not accept every registration.",
                        2,
                        1,
                        1,
                        true,
                        List.of(acceptedDevice(), new DevicePushResult(
                                UUID.randomUUID(), "device-b", "ANDROID", "STALE", "UNREGISTERED"))));

        AttemptItem result = service.send(adminId, request(NotificationType.MOVE_IN_COMPLETED));

        assertThat(result.getStatus()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.getAcceptedCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.isClientReceiptObserved()).isFalse();
    }

    @Test
    void browserChannelIsNotReportedAsSuccess() {
        AttemptItem result = service.send(adminId, request(NotificationType.MENU_PUBLISHED, "BROWSER"));

        assertThat(result.getStatus()).isEqualTo("BLOCKED");
        assertThat(result.getErrorCode()).isEqualTo("BROWSER_PUSH_NOT_IMPLEMENTED");
        verify(notificationService, never()).publish(any());
        verify(pushNotificationService, never()).deliverTracked(anyString(), anyString(), anyMap(), anyList());
    }

    @Test
    void repeatedClientRequestDoesNotSendTwice() {
        when(deviceTokenService.listActiveForUser(adminId)).thenReturn(List.of(device(adminId)));
        when(notificationService.publish(any())).thenReturn(NotificationResponse.builder()
                .notificationId(UUID.randomUUID())
                .build());
        when(pushNotificationService.deliverTracked(anyString(), anyString(), anyMap(), anyList()))
                .thenReturn(TrackedPushResult.blocked("PUSH_DISABLED", "off"));
        AtomicReference<AdminNotificationTestAttemptEntity> stored = new AtomicReference<>();
        doAnswer(invocation -> {
            AdminNotificationTestAttemptEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            stored.set(entity);
            return entity;
        }).when(attemptRepository).save(any());
        when(attemptRepository.findByAdminUserIdAndClientRequestId(adminId, "req-1"))
                .thenReturn(Optional.empty())
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));

        AdminNotificationTestRequest request = request(NotificationType.PASSWORD_CHANGED);
        request.setClientRequestId("req-1");
        service.send(adminId, request);
        AttemptItem second = service.send(adminId, request);

        assertThat(second.getNotificationType()).isEqualTo("PASSWORD_CHANGED");
        verify(notificationService).publish(any());
    }

    @Test
    void secondSendInsideTheGapIsRateLimited() {
        AdminNotificationTestRateLimit limit =
                new AdminNotificationTestRateLimit(Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC));
        limit.acquire(adminId);
        limit.release(adminId);
        assertThatThrownBy(() -> limit.acquire(adminId)).isInstanceOf(RateLimitedException.class);
    }

    @Test
    void deleteOneRemovesOnlyTheAdminTestRow() {
        UUID attemptId = UUID.randomUUID();
        when(attemptRepository.deleteOwned(attemptId, adminId)).thenReturn(1);

        var result = service.deleteOne(adminId, attemptId);

        assertThat(result.getDeletedCount()).isEqualTo(1);
        verify(notificationService, never()).publish(any());
        verify(securityAudit).record(adminId, adminId, "ADMIN_NOTIFICATION_TEST_DELETE", "deleted=1");
    }

    @Test
    void repeatedDeleteOfAMissingTestRowDeletesNothing() {
        UUID attemptId = UUID.randomUUID();
        when(attemptRepository.deleteOwned(attemptId, adminId)).thenReturn(0);

        assertThat(service.deleteOne(adminId, attemptId).getDeletedCount()).isZero();
        verify(notificationService, never()).publish(any());
    }

    @Test
    void clearAllDeletesOnlyThisAdminTestHistory() {
        when(attemptRepository.deleteOwnedByAdmin(adminId)).thenReturn(4);

        assertThat(service.deleteAll(adminId).getDeletedCount()).isEqualTo(4);
        verify(securityAudit).record(adminId, adminId, "ADMIN_NOTIFICATION_TEST_CLEAR", "deleted=4");
    }

    private AdminNotificationTestRequest request(NotificationType type) {
        return request(type, "PUSH");
    }

    private AdminNotificationTestRequest request(NotificationType type, String channel) {
        AdminNotificationTestRequest request = new AdminNotificationTestRequest();
        request.setNotificationType(type);
        request.setTestMode(true);
        request.setChannel(channel);
        request.setTitle("ACOMI Test");
        request.setBody("Harmless test body");
        return request;
    }

    private static UserDeviceTokenEntity device(UUID userId) {
        UserDeviceTokenEntity entity = UserDeviceTokenEntity.builder()
                .userId(userId)
                .fcmToken("secret-token-value")
                .platform(DevicePlatform.ANDROID)
                .deviceId("device-a")
                .active(true)
                .build();
        entity.setId(UUID.randomUUID());
        return entity;
    }

    private static DevicePushResult acceptedDevice() {
        return new DevicePushResult(UUID.randomUUID(), "device-a", "ANDROID", "ACCEPTED", null);
    }
}
