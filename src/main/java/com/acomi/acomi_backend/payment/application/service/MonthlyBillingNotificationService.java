package com.acomi.acomi_backend.payment.application.service;

import com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand;
import com.acomi.acomi_backend.notification.application.service.DeviceTokenService;
import com.acomi.acomi_backend.notification.application.service.NotificationService;
import com.acomi.acomi_backend.notification.application.service.PushNotificationService;
import com.acomi.acomi_backend.notification.application.service.PushNotificationService.TrackedPushResult;
import com.acomi.acomi_backend.notification.domain.model.NotificationCategory;
import com.acomi.acomi_backend.notification.domain.model.NotificationEntityType;
import com.acomi.acomi_backend.notification.domain.model.NotificationPriority;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.notification.infrastructure.persistence.entity.UserDeviceTokenEntity;
import com.acomi.acomi_backend.payment.domain.model.MonthlyBillingNotificationStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.MonthlyBillingNotificationEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.MonthlyBillingNotificationRepository;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable outbox for "monthly rent generated" pushes.
 * Payment creation never waits on Firebase. A failed send retries this row only.
 * FCM acceptance is recorded as provider acceptance, not as proof the phone displayed it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyBillingNotificationService {

    public static final int MAX_ATTEMPTS = 8;
    private static final DateTimeFormatter MONTH =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DUE =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private final MonthlyBillingNotificationRepository notificationRepository;
    private final SpacePaymentRepository paymentRepository;
    private final NotificationService notificationService;
    private final PushNotificationService pushNotificationService;
    private final DeviceTokenService deviceTokenService;

    /** @return true when a pending customer push was queued */
    @Transactional
    public boolean enqueue(SpacePaymentEntity payment, YearMonth month) {
        if (payment == null || payment.getId() == null || payment.getMember() == null || month == null) {
            return false;
        }
        if (notificationRepository
                .findByPaymentIdAndEventType(
                        payment.getId(), MonthlyBillingNotificationEntity.EVENT_MONTHLY_RENT_GENERATED)
                .isPresent()) {
            return false;
        }
        UUID userId = payment.getMember().getUser() != null ? payment.getMember().getUser().getId() : null;
        boolean pending = userId != null;
        notificationRepository.save(MonthlyBillingNotificationEntity.builder()
                .spaceId(payment.getSpace().getId())
                .paymentId(payment.getId())
                .recipientMemberId(payment.getMember().getId())
                .recipientUserId(userId)
                .eventType(MonthlyBillingNotificationEntity.EVENT_MONTHLY_RENT_GENERATED)
                .billingMonth(month.atDay(1))
                .status(pending
                        ? MonthlyBillingNotificationStatus.PENDING
                        : MonthlyBillingNotificationStatus.SKIPPED)
                .attemptCount(0)
                .lastError(pending ? null : "NO_CUSTOMER_ACCOUNT")
                .nextAttemptAt(pending ? LocalDateTime.now() : null)
                .build());
        return pending;
    }

    @Transactional
    public void reconcileMissingOutbox() {
        List<SpacePaymentEntity> missing = paymentRepository.findPendingAutomaticRentWithoutNotification(
                SpacePaymentGenerationService.AUTOMATIC_CALENDAR_MONTH_RENT,
                MonthlyBillingNotificationEntity.EVENT_MONTHLY_RENT_GENERATED,
                PageRequest.of(0, 40));
        for (SpacePaymentEntity payment : missing) {
            YearMonth month = payment.getMonth() != null ? YearMonth.parse(payment.getMonth()) : null;
            enqueue(payment, month);
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> dueIds(LocalDateTime now) {
        return notificationRepository
                .findDue(MonthlyBillingNotificationStatus.PENDING, now, PageRequest.of(0, 40))
                .stream()
                .map(MonthlyBillingNotificationEntity::getId)
                .toList();
    }

    @Transactional
    public void deliverOne(UUID outboxId) {
        MonthlyBillingNotificationEntity row = notificationRepository.findById(outboxId).orElse(null);
        if (row == null || row.getStatus() != MonthlyBillingNotificationStatus.PENDING) {
            return;
        }
        if (row.getAttemptCount() >= MAX_ATTEMPTS) {
            row.setStatus(MonthlyBillingNotificationStatus.FAILED);
            row.setLastError(trim(row.getLastError() != null ? row.getLastError() : "MAX_ATTEMPTS"));
            notificationRepository.save(row);
            return;
        }

        SpacePaymentEntity payment = paymentRepository.findById(row.getPaymentId()).orElse(null);
        if (payment == null
                || payment.getPaymentStatus() == SpacePaymentStatus.CANCELLED
                || payment.getPaymentStatus() == SpacePaymentStatus.REFUNDED) {
            row.setStatus(MonthlyBillingNotificationStatus.SKIPPED);
            row.setLastError(payment == null ? "PAYMENT_MISSING" : "OBLIGATION_NOT_OPEN");
            row.setLastAttemptAt(LocalDateTime.now());
            notificationRepository.save(row);
            return;
        }
        if (row.getRecipientUserId() == null) {
            row.setStatus(MonthlyBillingNotificationStatus.SKIPPED);
            row.setLastError("NO_CUSTOMER_ACCOUNT");
            notificationRepository.save(row);
            return;
        }

        row.setAttemptCount(row.getAttemptCount() + 1);
        row.setLastAttemptAt(LocalDateTime.now());

        String title = "Monthly rent payment generated";
        String body = message(payment);
        publishInApp(payment, row.getRecipientUserId(), title, body);

        List<UserDeviceTokenEntity> devices = deviceTokenService.listActiveForUser(row.getRecipientUserId());
        if (devices.isEmpty()) {
            scheduleRetry(row, "NO_ACTIVE_TOKEN");
            notificationRepository.save(row);
            return;
        }

        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", NotificationType.PAYMENT_GENERATED.name());
        data.put("spaceId", payment.getSpace().getId().toString());
        data.put("entityType", NotificationEntityType.PAYMENT.name());
        data.put("entityId", payment.getId().toString());
        data.put("actionRoute", "PaymentDetail");
        TrackedPushResult result = pushNotificationService.deliverTracked(title, body, data, devices);
        if (result.accepted() > 0) {
            row.setStatus(MonthlyBillingNotificationStatus.FCM_ACCEPTED);
            row.setFcmAcceptedAt(LocalDateTime.now());
            row.setLastError(result.failed() > 0 ? trim(result.errorCode()) : null);
            row.setNextAttemptAt(null);
        } else {
            scheduleRetry(row, result.errorCode() != null ? result.errorCode() : result.status());
        }
        notificationRepository.save(row);
    }

    /** Resets failed rows so the worker can send again. Does not create a payment. */
    @Transactional
    public int reopenFailed() {
        List<MonthlyBillingNotificationEntity> failed = notificationRepository.findByStatus(
                MonthlyBillingNotificationStatus.FAILED, PageRequest.of(0, 100));
        LocalDateTime now = LocalDateTime.now();
        for (MonthlyBillingNotificationEntity row : failed) {
            row.setStatus(MonthlyBillingNotificationStatus.PENDING);
            row.setAttemptCount(0);
            row.setNextAttemptAt(now);
            row.setLastError(null);
        }
        notificationRepository.saveAll(failed);
        return failed.size();
    }

    private void publishInApp(SpacePaymentEntity payment, UUID userId, String title, String body) {
        notificationService.publish(PublishNotificationCommand.builder()
                .spaceId(payment.getSpace().getId())
                .userId(userId)
                .entityType(NotificationEntityType.PAYMENT)
                .entityId(payment.getId())
                .notificationType(NotificationType.PAYMENT_GENERATED)
                .category(NotificationCategory.INFORMATION)
                .priority(NotificationPriority.HIGH)
                .title(title)
                .message(body)
                .actionLabel("View payment")
                .actionRoute("PaymentDetail")
                .dedupeKey("PAYMENT_GENERATED:" + payment.getId() + ":" + userId)
                .deliveryChannels(List.of("IN_APP", "PUSH"))
                .suppressAsyncPush(true)
                .build());
    }

    private static void scheduleRetry(MonthlyBillingNotificationEntity row, String error) {
        row.setLastError(trim(error));
        if (row.getAttemptCount() >= MAX_ATTEMPTS) {
            row.setStatus(MonthlyBillingNotificationStatus.FAILED);
            row.setNextAttemptAt(null);
            return;
        }
        row.setStatus(MonthlyBillingNotificationStatus.PENDING);
        row.setNextAttemptAt(LocalDateTime.now().plusMinutes(15L * row.getAttemptCount()));
    }

    static String message(SpacePaymentEntity payment) {
        SpaceEntity space = payment.getSpace();
        String spaceName = space != null && space.getName() != null && !space.getName().isBlank()
                ? space.getName()
                : "your space";
        YearMonth month = payment.getMonth() != null ? YearMonth.parse(payment.getMonth()) : YearMonth.now();
        LocalDate due = payment.getDueDate() != null ? payment.getDueDate() : month.atDay(1);
        return "Your " + MONTH.format(month) + " rent of " + inr(payment.getAmount())
                + " has been generated for " + spaceName
                + ". Due date: " + DUE.format(due)
                + ". Open ACOMI to view payment details.";
    }

    static String inr(BigDecimal amount) {
        BigDecimal value = amount != null ? amount : BigDecimal.ZERO;
        boolean whole = value.stripTrailingZeros().scale() <= 0;
        java.text.NumberFormat format = java.text.NumberFormat.getNumberInstance(Locale.ENGLISH);
        format.setGroupingUsed(true);
        format.setMaximumFractionDigits(whole ? 0 : 2);
        format.setMinimumFractionDigits(whole ? 0 : 2);
        return "₹" + format.format(value);
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
