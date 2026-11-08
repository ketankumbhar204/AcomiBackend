package com.acomi.acomi_backend.payment.application.service;

import com.acomi.acomi_backend.payment.domain.model.MonthlyBillingNotificationStatus;
import com.acomi.acomi_backend.payment.domain.model.MonthlyBillingRunStatus;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.MonthlyBillingNotificationEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.MonthlyBillingRunEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.MonthlyBillingNotificationRepository;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.MonthlyBillingRunRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MonthlyBillingMonitorService {

    private final MonthlyBillingRunRepository runRepository;
    private final MonthlyBillingNotificationRepository notificationRepository;
    private final MonthlyCalendarBillingService billingService;
    private final MonthlyBillingNotificationService notificationService;

    @Transactional(readOnly = true)
    public MonitorSnapshot snapshot() {
        MonthlyBillingRunEntity lastSuccess = runRepository
                .findFirstByStatusOrderByFinishedAtDesc(MonthlyBillingRunStatus.SUCCESS)
                .orElse(null);
        List<RunItem> runs = runRepository.findAllByOrderByStartedAtDesc(PageRequest.of(0, 40)).stream()
                .map(RunItem::from)
                .toList();
        List<NotificationItem> notifications = notificationRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, 30))
                .stream()
                .map(NotificationItem::from)
                .toList();
        return MonitorSnapshot.builder()
                .lastSuccessfulFinishedAt(lastSuccess != null ? lastSuccess.getFinishedAt() : null)
                .notificationsPending(notificationRepository.countByStatus(MonthlyBillingNotificationStatus.PENDING))
                .notificationsAcceptedByFirebase(
                        notificationRepository.countByStatus(MonthlyBillingNotificationStatus.FCM_ACCEPTED))
                .notificationsFailed(notificationRepository.countByStatus(MonthlyBillingNotificationStatus.FAILED))
                .notificationsSkipped(notificationRepository.countByStatus(MonthlyBillingNotificationStatus.SKIPPED))
                .runs(runs)
                .recentNotifications(notifications)
                .build();
    }

    public MonitorSnapshot runNow() {
        billingService.runDueCycles();
        return snapshot();
    }

    public MonitorSnapshot retryFailedNotifications() {
        notificationService.reopenFailed();
        for (UUID id : notificationService.dueIds(LocalDateTime.now())) {
            notificationService.deliverOne(id);
        }
        return snapshot();
    }

    @Getter
    @Builder
    public static class MonitorSnapshot {
        private final LocalDateTime lastSuccessfulFinishedAt;
        private final long notificationsPending;
        private final long notificationsAcceptedByFirebase;
        private final long notificationsFailed;
        private final long notificationsSkipped;
        private final List<RunItem> runs;
        private final List<NotificationItem> recentNotifications;
    }

    @Getter
    @Builder
    public static class RunItem {
        private final UUID id;
        private final UUID spaceId;
        private final LocalDate billingMonth;
        private final String businessTimezone;
        private final String status;
        private final LocalDateTime startedAt;
        private final LocalDateTime finishedAt;
        private final int eligibleAccommodation;
        private final int eligibleMess;
        private final int paymentsCreated;
        private final int paymentsSkipped;
        private final int paymentsFailed;
        private final int notificationsQueued;
        private final String skipSummary;
        private final String errorMessage;

        static RunItem from(MonthlyBillingRunEntity run) {
            return RunItem.builder()
                    .id(run.getId())
                    .spaceId(run.getSpaceId())
                    .billingMonth(run.getBillingMonth())
                    .businessTimezone(run.getBusinessTimezone())
                    .status(run.getStatus().name())
                    .startedAt(run.getStartedAt())
                    .finishedAt(run.getFinishedAt())
                    .eligibleAccommodation(run.getEligibleAccommodation())
                    .eligibleMess(run.getEligibleMess())
                    .paymentsCreated(run.getPaymentsCreated())
                    .paymentsSkipped(run.getPaymentsSkipped())
                    .paymentsFailed(run.getPaymentsFailed())
                    .notificationsQueued(run.getNotificationsQueued())
                    .skipSummary(run.getSkipSummary())
                    .errorMessage(run.getErrorMessage())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class NotificationItem {
        private final UUID id;
        private final UUID spaceId;
        private final UUID paymentId;
        private final UUID recipientUserId;
        private final LocalDate billingMonth;
        private final String status;
        private final int attemptCount;
        private final LocalDateTime lastAttemptAt;
        private final String lastError;
        private final LocalDateTime fcmAcceptedAt;

        static NotificationItem from(MonthlyBillingNotificationEntity row) {
            return NotificationItem.builder()
                    .id(row.getId())
                    .spaceId(row.getSpaceId())
                    .paymentId(row.getPaymentId())
                    .recipientUserId(row.getRecipientUserId())
                    .billingMonth(row.getBillingMonth())
                    .status(row.getStatus().name())
                    .attemptCount(row.getAttemptCount())
                    .lastAttemptAt(row.getLastAttemptAt())
                    .lastError(row.getLastError())
                    .fcmAcceptedAt(row.getFcmAcceptedAt())
                    .build();
        }
    }
}
