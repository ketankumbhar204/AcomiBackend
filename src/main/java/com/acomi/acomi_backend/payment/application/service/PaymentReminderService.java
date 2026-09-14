package com.acomi.acomi_backend.payment.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.domain.model.MembershipStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand;
import com.acomi.acomi_backend.notification.application.service.NotificationService;
import com.acomi.acomi_backend.notification.domain.model.NotificationCategory;
import com.acomi.acomi_backend.notification.domain.model.NotificationEntityType;
import com.acomi.acomi_backend.notification.domain.model.NotificationPriority;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.payment.api.dto.response.OverduePaymentResponse;
import com.acomi.acomi_backend.payment.api.dto.response.PaymentReminderDeliveryResponse;
import com.acomi.acomi_backend.payment.api.dto.response.PaymentReminderProcessResultResponse;
import com.acomi.acomi_backend.payment.application.dto.PaymentReminderMessage;
import com.acomi.acomi_backend.payment.application.port.out.MessageDeliveryProvider;
import com.acomi.acomi_backend.payment.application.support.PaymentDueStatusCalculator;
import com.acomi.acomi_backend.payment.domain.model.PaymentReminderType;
import com.acomi.acomi_backend.payment.domain.model.PaymentTimelineEventType;
import com.acomi.acomi_backend.payment.domain.model.ReminderChannel;
import com.acomi.acomi_backend.payment.domain.model.ReminderDeliveryStatus;
import com.acomi.acomi_backend.payment.infrastructure.delivery.WhatsAppMessageDeliveryProvider;
import com.acomi.acomi_backend.payment.infrastructure.delivery.WhatsAppReminderProperties;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.PaymentReminderDeliveryEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.PaymentReminderDeliveryRepository;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orchestrates overdue payment reminders.
 *
 * <p>Flow: eligibility (Phase 2) → claim delivery row → channel adapter → record result.
 * Does not recalculate rent/tax or mutate payment amounts/status.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentReminderService {

    private final PaymentReminderEligibilityService eligibilityService;
    private final SpacePaymentAccessService accessService;
    private final SpaceRepository spaceRepository;
    private final SpacePaymentRepository paymentRepository;
    private final PaymentReminderDeliveryRepository deliveryRepository;
    private final WhatsAppMessageDeliveryProvider whatsAppProvider;
    private final WhatsAppReminderProperties whatsAppProperties;
    private final SpacePaymentTimelineService timelineService;
    private final NotificationService notificationService;
    private final SpaceMembershipRepository membershipRepository;
    private final TransactionTemplate transactionTemplate;

    @Transactional(readOnly = true)
    public boolean isWhatsAppConfigured() {
        return whatsAppProvider.isConfigured();
    }

    public PaymentReminderProcessResultResponse processSpace(UUID spaceId, UUID callerId) {
        accessService.requireManagePayments(spaceId, callerId);
        return processSpaceInternal(spaceId, callerId, false);
    }

    /** Scheduler entry — no user auth; processes one active space. */
    public PaymentReminderProcessResultResponse processSpaceAsSystem(UUID spaceId) {
        return processSpaceInternal(spaceId, null, true);
    }

    public PaymentReminderDeliveryResponse processPayment(UUID spaceId, UUID paymentId, UUID callerId) {
        accessService.requireManagePayments(spaceId, callerId);
        SpaceEntity space = loadSpace(spaceId);
        LocalDate businessDate = PaymentDueStatusCalculator.businessDate(space);

        List<OverduePaymentResponse> eligible =
                eligibilityService.findObligationsNeedingReminderInternal(spaceId);
        OverduePaymentResponse target = eligible.stream()
                .filter(row -> paymentId.equals(row.getPaymentId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "Payment is not reminder-eligible", HttpStatus.CONFLICT));

        AttemptOutcome outcome = attemptReminder(space, target, businessDate, callerId);
        return toDeliveryResponse(outcome.delivery());
    }

    private PaymentReminderProcessResultResponse processSpaceInternal(
            UUID spaceId, UUID actorId, boolean systemRun) {
        SpaceEntity space = loadSpace(spaceId);
        if (!space.isActive()) {
            throw new BusinessException("Space is not active", HttpStatus.CONFLICT);
        }

        LocalDate businessDate = PaymentDueStatusCalculator.businessDate(space);
        List<OverduePaymentResponse> candidates = systemRun
                ? eligibilityService.findObligationsNeedingReminderInternal(spaceId)
                : eligibilityService.findObligationsNeedingReminder(spaceId, actorId);

        log.info(
                "Payment reminder processing started space={} businessDate={} candidates={} systemRun={}",
                spaceId,
                businessDate,
                candidates.size(),
                systemRun);

        int created = 0;
        int skipped = 0;
        int successes = 0;
        int failures = 0;
        List<PaymentReminderDeliveryResponse> deliveries = new ArrayList<>();

        for (OverduePaymentResponse candidate : candidates) {
            AttemptOutcome outcome = attemptReminder(space, candidate, businessDate, actorId);
            deliveries.add(toDeliveryResponse(outcome.delivery()));
            switch (outcome.kind()) {
                case CREATED_SENT -> {
                    created++;
                    successes++;
                }
                case CREATED_FAILED -> {
                    created++;
                    failures++;
                }
                case RETRIED_SENT -> successes++;
                case RETRIED_FAILED -> failures++;
                case SKIPPED_DUPLICATE -> skipped++;
                case SKIPPED_IN_PROGRESS -> skipped++;
            }
        }

        log.info(
                "Payment reminder processing finished space={} businessDate={} candidates={} "
                        + "created={} skipped={} successes={} failures={}",
                spaceId,
                businessDate,
                candidates.size(),
                created,
                skipped,
                successes,
                failures);

        return PaymentReminderProcessResultResponse.builder()
                .businessDate(businessDate)
                .providerConfigured(whatsAppProvider.isConfigured())
                .providerMode(whatsAppProvider.getMode())
                .candidatesFound(candidates.size())
                .remindersCreated(created)
                .remindersSkippedDuplicate(skipped)
                .deliverySuccesses(successes)
                .deliveryFailures(failures)
                .deliveries(deliveries)
                .build();
    }

    private AttemptOutcome attemptReminder(
            SpaceEntity space,
            OverduePaymentResponse candidate,
            LocalDate businessDate,
            UUID actorId) {
        PaymentReminderType reminderType =
                PaymentReminderType.fromPaymentType(candidate.getPaymentType());
        ReminderChannel channel = ReminderChannel.WHATSAPP;

        ClaimResult claim = claimDelivery(space, candidate, reminderType, channel, businessDate);
        if (claim.kind() == ClaimKind.SKIP_SENT || claim.kind() == ClaimKind.SKIP_PENDING) {
            log.info(
                    "Reminder skipped payment={} reason={} businessDate={}",
                    candidate.getPaymentId(),
                    claim.kind() == ClaimKind.SKIP_SENT ? "already processed today" : "in progress",
                    businessDate);
            return new AttemptOutcome(
                    claim.kind() == ClaimKind.SKIP_SENT
                            ? AttemptKind.SKIPPED_DUPLICATE
                            : AttemptKind.SKIPPED_IN_PROGRESS,
                    claim.entity());
        }

        PaymentReminderMessage message = buildMessage(space, candidate, reminderType, channel, businessDate);

        // External call outside long-lived payment mutation TX (delivery row already PENDING).
        MessageDeliveryProvider.MessageDeliveryResult result = whatsAppProvider.deliver(message);

        PaymentReminderDeliveryEntity updated = transactionTemplate.execute(status ->
                finalizeDeliveryInTx(
                        claim.entity().getId(),
                        result,
                        candidate.getPaymentId(),
                        space.getId(),
                        actorId,
                        claim.kind() == ClaimKind.NEW));

        if (result.isSuccess()) {
            log.info(
                    "Reminder delivered payment={} recipientMember={} daysOverdue={} channel={}",
                    candidate.getPaymentId(),
                    candidate.getMemberId(),
                    candidate.getDaysOverdue(),
                    channel);
            return new AttemptOutcome(
                    claim.kind() == ClaimKind.RETRY_FAILED
                            ? AttemptKind.RETRIED_SENT
                            : AttemptKind.CREATED_SENT,
                    updated);
        }

        log.warn(
                "Reminder delivery failed payment={} channel={} reason={}",
                candidate.getPaymentId(),
                channel,
                result.getFailureReason());
        return new AttemptOutcome(
                claim.kind() == ClaimKind.RETRY_FAILED
                        ? AttemptKind.RETRIED_FAILED
                        : AttemptKind.CREATED_FAILED,
                updated);
    }

    private ClaimResult claimDelivery(
            SpaceEntity space,
            OverduePaymentResponse candidate,
            PaymentReminderType reminderType,
            ReminderChannel channel,
            LocalDate businessDate) {
        return transactionTemplate.execute(status -> {
            Optional<PaymentReminderDeliveryEntity> existing =
                    deliveryRepository.findByPaymentIdAndReminderTypeAndBusinessDateAndChannel(
                            candidate.getPaymentId(), reminderType, businessDate, channel);

            if (existing.isPresent()) {
                PaymentReminderDeliveryEntity row = existing.get();
                if (row.getDeliveryStatus() == ReminderDeliveryStatus.SENT) {
                    return new ClaimResult(ClaimKind.SKIP_SENT, row);
                }
                if (row.getDeliveryStatus() == ReminderDeliveryStatus.PENDING) {
                    return new ClaimResult(ClaimKind.SKIP_PENDING, row);
                }
                if (row.getDeliveryStatus() == ReminderDeliveryStatus.FAILED
                        || row.getDeliveryStatus() == ReminderDeliveryStatus.SKIPPED) {
                    if (isPermanentFailure(row.getFailureReason())) {
                        log.info(
                                "Reminder skipped payment={} reason=permanent_failure failure={}",
                                candidate.getPaymentId(),
                                row.getFailureReason());
                        return new ClaimResult(ClaimKind.SKIP_SENT, row);
                    }
                    int maxAttempts = Math.max(1, whatsAppProperties.getMaxTransientAttempts());
                    if (row.getAttemptCount() >= maxAttempts) {
                        log.info(
                                "Reminder skipped payment={} reason=max_attempts attempts={}",
                                candidate.getPaymentId(),
                                row.getAttemptCount());
                        return new ClaimResult(ClaimKind.SKIP_SENT, row);
                    }
                    row.setDeliveryStatus(ReminderDeliveryStatus.PENDING);
                    row.setAttemptCount(row.getAttemptCount() + 1);
                    row.setLastAttemptAt(LocalDateTime.now());
                    row.setFailureReason(null);
                    row.setOutstandingAmount(candidate.getOutstandingAmount());
                    row.setDaysOverdue(candidate.getDaysOverdue());
                    return new ClaimResult(ClaimKind.RETRY_FAILED, deliveryRepository.save(row));
                }
            }

            UUID recipientUserId = null;
            SpacePaymentEntity payment = paymentRepository
                    .findByIdAndSpaceId(candidate.getPaymentId(), space.getId())
                    .orElse(null);
            if (payment != null && payment.getMember() != null && payment.getMember().getUser() != null) {
                recipientUserId = payment.getMember().getUser().getId();
            }

            PaymentReminderDeliveryEntity created = PaymentReminderDeliveryEntity.builder()
                    .spaceId(space.getId())
                    .paymentId(candidate.getPaymentId())
                    .recipientMemberId(candidate.getMemberId())
                    .recipientUserId(recipientUserId)
                    .recipientMobile(candidate.getMemberMobile())
                    .reminderType(reminderType)
                    .channel(channel)
                    .businessDate(businessDate)
                    .deliveryStatus(ReminderDeliveryStatus.PENDING)
                    .outstandingAmount(candidate.getOutstandingAmount())
                    .currencyCode(candidate.getCurrencyCode())
                    .daysOverdue(candidate.getDaysOverdue())
                    .attemptCount(1)
                    .lastAttemptAt(LocalDateTime.now())
                    .build();

            try {
                return new ClaimResult(ClaimKind.NEW, deliveryRepository.saveAndFlush(created));
            } catch (DataIntegrityViolationException ex) {
                // Concurrent claim won the unique constraint — treat as duplicate.
                PaymentReminderDeliveryEntity raced =
                        deliveryRepository
                                .findByPaymentIdAndReminderTypeAndBusinessDateAndChannel(
                                        candidate.getPaymentId(), reminderType, businessDate, channel)
                                .orElseThrow(() -> ex);
                if (raced.getDeliveryStatus() == ReminderDeliveryStatus.SENT) {
                    return new ClaimResult(ClaimKind.SKIP_SENT, raced);
                }
                return new ClaimResult(ClaimKind.SKIP_PENDING, raced);
            }
        });
    }

    private PaymentReminderDeliveryEntity finalizeDeliveryInTx(
            UUID deliveryId,
            MessageDeliveryProvider.MessageDeliveryResult result,
            UUID paymentId,
            UUID spaceId,
            UUID actorId,
            boolean firstAttempt) {
        PaymentReminderDeliveryEntity row = deliveryRepository
                .findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentReminderDelivery", "id", deliveryId));

        LocalDateTime now = LocalDateTime.now();
        row.setLastAttemptAt(now);

        if (result.isSuccess()) {
            row.setDeliveryStatus(ReminderDeliveryStatus.SENT);
            row.setProviderMessageId(result.getProviderMessageId());
            row.setFailureReason(null);
            row.setSentAt(now);
        } else {
            row.setDeliveryStatus(ReminderDeliveryStatus.FAILED);
            String code = result.getFailureCode() != null ? result.getFailureCode() : "PROVIDER_ERROR";
            String detail = result.getFailureReason() != null ? result.getFailureReason() : code;
            row.setFailureReason(truncate(code + ": " + detail, 500));
        }

        PaymentReminderDeliveryEntity saved = deliveryRepository.save(row);

        SpacePaymentEntity payment = paymentRepository
                .findByIdAndSpaceId(paymentId, spaceId)
                .orElse(null);
        if (payment != null) {
            if (result.isSuccess()) {
                timelineService.record(
                        payment,
                        PaymentTimelineEventType.REMINDER_SENT,
                        "WhatsApp reminder · " + row.getBusinessDate(),
                        actorId);
                publishInAppReminderSent(payment, row, actorId);
            } else if (firstAttempt || row.getAttemptCount() <= 3) {
                timelineService.record(
                        payment,
                        PaymentTimelineEventType.REMINDER_FAILED,
                        truncate(result.getFailureReason(), 200),
                        actorId);
            }
        }

        return saved;
    }

    private void publishInAppReminderSent(
            SpacePaymentEntity payment, PaymentReminderDeliveryEntity delivery, UUID actorId) {
        UUID spaceId = payment.getSpace().getId();
        String memberLabel = payment.getMember() != null && payment.getMember().getFullName() != null
                ? payment.getMember().getFullName()
                : "A member";
        String managerMessage = memberLabel + " was reminded about an overdue payment.";
        String tenantMessage = "A payment is overdue. Open the app to review.";

        List<UUID> managerIds = membershipRepository
                .findBySpaceIdAndStatus(spaceId, MembershipStatus.ACTIVE)
                .stream()
                .filter(m -> m.getRole() == MembershipRole.OWNER || m.getRole() == MembershipRole.MANAGER)
                .map(m -> m.getUser().getId())
                .toList();

        for (UUID managerId : managerIds) {
            notificationService.publish(PublishNotificationCommand.builder()
                    .spaceId(spaceId)
                    .userId(managerId)
                    .actorId(actorId)
                    .entityType(NotificationEntityType.PAYMENT)
                    .entityId(payment.getId())
                    .notificationType(NotificationType.PAYMENT_REMINDER_SENT)
                    .category(NotificationCategory.INFORMATION)
                    .priority(NotificationPriority.LOW)
                    .title("Payment reminder sent")
                    .message(managerMessage)
                    .actionLabel("View Payment")
                    .actionRoute("PaymentDetail")
                    .dedupeKey("INFO:PAYMENT_REMINDER_SENT:"
                            + payment.getId()
                            + ":"
                            + delivery.getBusinessDate()
                            + ":"
                            + managerId)
                    .deliveryChannels(List.of("IN_APP", "WHATSAPP"))
                    .build());
        }

        MemberEntity member = payment.getMember();
        if (member.getUser() != null) {
            UUID tenantId = member.getUser().getId();
            notificationService.publish(PublishNotificationCommand.builder()
                    .spaceId(spaceId)
                    .userId(tenantId)
                    .actorId(actorId)
                    .entityType(NotificationEntityType.PAYMENT)
                    .entityId(payment.getId())
                    .notificationType(NotificationType.PAYMENT_REMINDER_SENT)
                    .category(NotificationCategory.WARNING)
                    .priority(NotificationPriority.MEDIUM)
                    .title("Payment reminder")
                    .message(tenantMessage)
                    .actionLabel("View Payment")
                    .actionRoute("PaymentDetail")
                    .dedupeKey("INFO:PAYMENT_REMINDER_SENT:"
                            + payment.getId()
                            + ":"
                            + delivery.getBusinessDate()
                            + ":"
                            + tenantId)
                    .deliveryChannels(List.of("IN_APP"))
                    .build());
        }
    }

    private PaymentReminderMessage buildMessage(
            SpaceEntity space,
            OverduePaymentResponse candidate,
            PaymentReminderType reminderType,
            ReminderChannel channel,
            LocalDate businessDate) {
        return PaymentReminderMessage.builder()
                .paymentId(candidate.getPaymentId())
                .spaceId(space.getId())
                .spaceName(space.getName())
                .memberId(candidate.getMemberId())
                .memberName(candidate.getMemberName())
                .recipientMobile(candidate.getMemberMobile())
                .paymentType(candidate.getPaymentType())
                .reminderType(reminderType)
                .channel(channel)
                .title(candidate.getTitle())
                .month(candidate.getMonth())
                .billingPeriodStart(candidate.getBillingPeriodStart())
                .billingPeriodEnd(candidate.getBillingPeriodEnd())
                .dueDate(candidate.getDueDate())
                .businessDate(businessDate)
                .daysOverdue(candidate.getDaysOverdue())
                .outstandingAmount(candidate.getOutstandingAmount())
                .currencyCode(candidate.getCurrencyCode())
                .build();
    }

    private SpaceEntity loadSpace(UUID spaceId) {
        return spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));
    }

    private PaymentReminderDeliveryResponse toDeliveryResponse(PaymentReminderDeliveryEntity entity) {
        String failureReason = entity.getFailureReason();
        String failureCode = extractFailureCode(failureReason);
        return PaymentReminderDeliveryResponse.builder()
                .deliveryId(entity.getId())
                .paymentId(entity.getPaymentId())
                .channel(entity.getChannel())
                .deliveryStatus(entity.getDeliveryStatus())
                .businessDate(entity.getBusinessDate())
                .providerMessageId(entity.getProviderMessageId())
                .failureReason(failureReason)
                .failureCode(failureCode)
                .providerConfigured(whatsAppProvider.isConfigured())
                .retryable(isRetryableFailure(failureCode) && entity.getDeliveryStatus() == ReminderDeliveryStatus.FAILED)
                .sentAt(entity.getSentAt())
                .lastAttemptAt(entity.getLastAttemptAt())
                .attemptCount(entity.getAttemptCount())
                .build();
    }

    private static boolean isPermanentFailure(String failureReason) {
        String code = extractFailureCode(failureReason);
        return "INVALID_RECIPIENT".equals(code)
                || "RECIPIENT_MOBILE_MISSING".equals(code)
                || "TEMPLATE_ERROR".equals(code)
                || "PROVIDER_AUTH_FAILED".equals(code)
                || "WHATSAPP_PROVIDER_NOT_CONFIGURED".equals(code)
                || "PROVIDER_NOT_CONFIGURED".equals(code);
    }

    private static boolean isRetryableFailure(String failureCode) {
        if (failureCode == null) {
            return true;
        }
        return !isPermanentFailure(failureCode);
    }

    private static String extractFailureCode(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            return null;
        }
        int idx = failureReason.indexOf(':');
        if (idx > 0) {
            return failureReason.substring(0, idx).trim();
        }
        return failureReason.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private enum ClaimKind {
        NEW,
        RETRY_FAILED,
        SKIP_SENT,
        SKIP_PENDING
    }

    private enum AttemptKind {
        CREATED_SENT,
        CREATED_FAILED,
        RETRIED_SENT,
        RETRIED_FAILED,
        SKIPPED_DUPLICATE,
        SKIPPED_IN_PROGRESS
    }

    private record ClaimResult(ClaimKind kind, PaymentReminderDeliveryEntity entity) {}

    private record AttemptOutcome(AttemptKind kind, PaymentReminderDeliveryEntity delivery) {}
}
