package com.acomi.acomi_backend.payment.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.domain.model.MembershipStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.notification.api.dto.response.NotificationResponse;
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
import com.acomi.acomi_backend.payment.application.support.PaymentReminderClickToChat;
import com.acomi.acomi_backend.payment.domain.model.PaymentReminderType;
import com.acomi.acomi_backend.payment.domain.model.PaymentTimelineEventType;
import com.acomi.acomi_backend.payment.domain.model.ReminderChannel;
import com.acomi.acomi_backend.payment.domain.model.ReminderDeliveryStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import com.acomi.acomi_backend.payment.infrastructure.delivery.FcmPaymentReminderDeliveryProvider;
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
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private final FcmPaymentReminderDeliveryProvider fcmProvider;
    private final WhatsAppReminderProperties whatsAppProperties;
    private final SpacePaymentTimelineService timelineService;
    private final NotificationService notificationService;
    private final SpaceMembershipRepository membershipRepository;
    private final WhatsAppPhoneNormalizer phoneNormalizer;
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

    /**
     * Owner action: validate the payment, record an in-app reminder, and return a WhatsApp
     * click-to-chat link. Does not call the WhatsApp provider and does not change the payment.
     */
    public PaymentReminderDeliveryResponse prepareOwnerReminder(UUID spaceId, UUID paymentId, UUID callerId) {
        accessService.requireManagePayments(spaceId, callerId);
        SpaceEntity space = loadSpace(spaceId);
        LocalDate businessDate = PaymentDueStatusCalculator.businessDate(space);

        SpacePaymentEntity payment = paymentRepository
                .findByIdAndSpaceId(paymentId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("SpacePayment", "id", paymentId));
        OverduePaymentResponse target = ownerReminderTarget(space, payment, businessDate);

        UserEntity clickToChatUser = payment.getMember() != null ? payment.getMember().getUser() : null;
        PaymentReminderMessage reminder = buildMessage(
                space, target, PaymentReminderType.fromPaymentType(target.getPaymentType()),
                ReminderChannel.WHATSAPP, businessDate,
                clickToChatUser != null ? clickToChatUser.getId() : null);
        String targetLabel = payment.getTargetLabel();
        String messageText = PaymentReminderClickToChat.message(reminder, targetLabel);
        String inAppText = PaymentReminderClickToChat.inAppMessage(reminder, targetLabel);

        var phone = phoneNormalizer.normalize(reminder.getRecipientMobile());
        String digits = phone.map(WhatsAppPhoneNormalizer.NormalizedPhone::digitsE164WithoutPlus).orElse(null);
        String whatsAppUrl = PaymentReminderClickToChat.waMeUrl(digits, messageText);

        boolean inAppRecorded = false;
        String inAppFailureCode = null;
        UserEntity memberUser = payment.getMember() != null ? payment.getMember().getUser() : null;
        if (memberUser == null || memberUser.getId() == null) {
            inAppFailureCode = "MEMBER_NOT_ON_ACOMI";
        } else {
            try {
                LocalDateTime started = LocalDateTime.now().minusSeconds(2);
                NotificationResponse published = notificationService.publish(PublishNotificationCommand.builder()
                        .spaceId(spaceId)
                        .userId(memberUser.getId())
                        .actorId(callerId)
                        .entityType(NotificationEntityType.PAYMENT)
                        .entityId(paymentId)
                        .notificationType(NotificationType.PAYMENT_REMINDER_SENT)
                        .category(NotificationCategory.WARNING)
                        .priority(NotificationPriority.MEDIUM)
                        .title("Payment Reminder")
                        .message(inAppText)
                        .actionLabel("View Payment")
                        .actionRoute("PaymentDetail")
                        .dedupeKey("INFO:PAYMENT_REMINDER_SENT:"
                                + paymentId
                                + ":"
                                + businessDate
                                + ":"
                                + memberUser.getId())
                        .deliveryChannels(List.of("IN_APP"))
                        .build());
                inAppRecorded = true;
                if (published.getCreatedAt() != null && published.getCreatedAt().isBefore(started)) {
                    log.info(
                            "Payment reminder reused existing in-app notification payment={} user={}",
                            paymentId,
                            memberUser.getId());
                }
            } catch (RuntimeException ex) {
                inAppFailureCode = "NOTIFICATION_FAILED";
                log.warn(
                        "Payment reminder in-app notification failed payment={} space={}",
                        paymentId,
                        spaceId,
                        ex);
            }
        }

        String failureCode = null;
        String failureReason = null;
        if (digits == null) {
            failureCode = "INVALID_RECIPIENT";
            failureReason = "Recipient mobile number is missing or invalid";
        }
        boolean notificationFailed = "NOTIFICATION_FAILED".equals(inAppFailureCode);
        ReminderDeliveryStatus deliveryStatus = whatsAppUrl != null
                ? ReminderDeliveryStatus.PENDING
                : ReminderDeliveryStatus.FAILED;

        return PaymentReminderDeliveryResponse.builder()
                .paymentId(paymentId)
                .channel(ReminderChannel.WHATSAPP)
                .deliveryStatus(deliveryStatus)
                .businessDate(businessDate)
                .failureCode(failureCode)
                .failureReason(failureReason)
                .providerConfigured(whatsAppUrl != null)
                .retryable(notificationFailed)
                .messageText(messageText)
                .whatsAppUrl(whatsAppUrl)
                .internationalPhone(digits)
                .inAppRecorded(inAppRecorded)
                .inAppFailureCode(inAppFailureCode)
                .build();
    }

    private OverduePaymentResponse ownerReminderTarget(
            SpaceEntity space, SpacePaymentEntity payment, LocalDate businessDate) {
        OverduePaymentResponse listed = eligibilityService.findObligationsNeedingReminderInternal(space.getId())
                .stream()
                .filter(row -> row.getPaymentId() != null && row.getPaymentId().equals(payment.getId()))
                .findFirst()
                .orElse(null);
        if (listed != null) {
            return listed;
        }
        PaymentDueStatusCalculator.DueStatus due = PaymentDueStatusCalculator.calculate(payment, businessDate);
        if (!due.isReminderEligible() || payment.getMember() == null) {
            String reason = reminderIneligibilityReason(payment, due);
            log.info(
                    "Payment reminder rejected stage=eligibility payment={} reason={} fcmAttempted=false",
                    payment.getId(),
                    reason);
            throw new BusinessException(
                    "Payment is not reminder-eligible (" + reason + ")", HttpStatus.CONFLICT);
        }
        return OverduePaymentResponse.builder()
                .paymentId(payment.getId())
                .spaceId(space.getId())
                .spaceName(space.getName())
                .memberId(payment.getMember().getId())
                .memberName(payment.getMember().getFullName())
                .memberMobile(payment.getMember().getMobileNumber())
                .paymentType(payment.getPaymentType())
                .paymentCategory(payment.getPaymentCategory())
                .title(payment.getTitle())
                .month(payment.getMonth())
                .billingPeriodStart(payment.getBillingPeriodStart())
                .billingPeriodEnd(payment.getBillingPeriodEnd())
                .dueDate(payment.getDueDate())
                .totalAmount(due.getTotalAmount())
                .paidAmount(due.getPaidAmount())
                .outstandingAmount(due.getOutstandingAmount())
                .currencyCode(payment.getCurrencyCode())
                .paymentStatus(payment.getPaymentStatus())
                .settlementStatus(due.getSettlementStatus())
                .overdue(due.isOverdue())
                .daysOverdue(due.getDaysOverdue())
                .reminderEligible(true)
                .build();
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
            // In-app + FCM overdue alerts are independent of WhatsApp delivery.
            try {
                publishOverdueAlerts(space, candidate);
            } catch (RuntimeException ex) {
                log.warn(
                        "Overdue in-app notification failed payment={} space={}",
                        candidate.getPaymentId(),
                        spaceId,
                        ex);
            }
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
                .providerConfigured(activeProviderConfigured())
                .providerMode(activeProviderMode())
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
        ReminderChannel channel = activeChannel();

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

        PaymentReminderMessage message = buildMessage(
                space,
                candidate,
                reminderType,
                channel,
                businessDate,
                claim.entity().getRecipientUserId());

        // External call outside long-lived payment mutation TX (delivery row already PENDING).
        // WhatsApp stays available for a future live mode, but a disabled provider is not called.
        MessageDeliveryProvider.MessageDeliveryResult result = channel == ReminderChannel.WHATSAPP
                ? whatsAppProvider.deliver(message)
                : fcmProvider.deliver(message);

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
                        (row.getChannel() == ReminderChannel.PUSH
                                        ? "Push notification accepted · "
                                        : "WhatsApp reminder · ")
                                + row.getBusinessDate(),
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

    /**
     * Persists the member overdue notice and a single owner action.
     * Deduped by payment, recipient and reminder stage so a scheduler rerun
     * does not create another row or another push. WhatsApp outcome is ignored.
     */
    private void publishOverdueAlerts(SpaceEntity space, OverduePaymentResponse candidate) {
        if (candidate.getPaymentId() == null || !candidate.isOverdue()) {
            return;
        }
        SpacePaymentEntity payment = paymentRepository
                .findByIdAndSpaceId(candidate.getPaymentId(), space.getId())
                .orElse(null);
        if (payment == null || payment.getMember() == null) {
            return;
        }
        String paymentLabel = paymentTypeLabel(candidate.getPaymentType());
        String amount = formatInr(candidate.getOutstandingAmount(), candidate.getCurrencyCode());
        String spaceName = space.getName() != null && !space.getName().isBlank() ? space.getName() : "your space";
        int days = Math.max(candidate.getDaysOverdue(), 1);
        String dayWord = days == 1 ? "day" : "days";
        String stage = overdueStage(days);

        UserEntity memberUser = payment.getMember().getUser();
        if (memberUser != null && memberUser.getId() != null) {
            notificationService.publish(PublishNotificationCommand.builder()
                    .spaceId(space.getId())
                    .userId(memberUser.getId())
                    .entityType(NotificationEntityType.PAYMENT)
                    .entityId(payment.getId() != null ? payment.getId() : candidate.getPaymentId())
                    .notificationType(NotificationType.PAYMENT_REMINDER_SENT)
                    .category(NotificationCategory.WARNING)
                    .priority(NotificationPriority.HIGH)
                    .title("Payment Overdue")
                    .message("Your " + paymentLabel + " payment of " + amount + " for " + spaceName
                            + " is overdue by " + days + " " + dayWord + ". Please complete the payment.")
                    .actionLabel("View Payment")
                    .actionRoute("PaymentDetail")
                    .dedupeKey("OVERDUE:MEMBER:" + stage + ":" + candidate.getPaymentId() + ":"
                            + memberUser.getId())
                    .deliveryChannels(List.of("IN_APP", "PUSH"))
                    .build());
        }

        String memberName = candidate.getMemberName() != null && !candidate.getMemberName().isBlank()
                ? candidate.getMemberName()
                : "A member";
        List<SpaceMembershipEntity> managers = membershipRepository.findBySpaceIdAndStatus(
                space.getId(), MembershipStatus.ACTIVE);
        for (SpaceMembershipEntity membership : managers) {
            if (membership.getRole() != MembershipRole.OWNER && membership.getRole() != MembershipRole.MANAGER) {
                continue;
            }
            if (membership.getUser() == null || membership.getUser().getId() == null) {
                continue;
            }
            UUID managerId = membership.getUser().getId();
            if (memberUser != null && managerId.equals(memberUser.getId())) {
                continue;
            }
            notificationService.publish(PublishNotificationCommand.builder()
                    .spaceId(space.getId())
                    .userId(managerId)
                    .entityType(NotificationEntityType.PAYMENT)
                    .entityId(payment.getId() != null ? payment.getId() : candidate.getPaymentId())
                    .notificationType(NotificationType.PAYMENT_OVERDUE)
                    .category(NotificationCategory.ACTION_REQUIRED)
                    .priority(NotificationPriority.HIGH)
                    .title("Member Payment Overdue")
                    .message(memberName + " has an overdue " + paymentLabel + " payment of " + amount
                            + " in " + spaceName + ".")
                    .actionLabel("View Payment")
                    .actionRoute("PaymentDetail")
                    .dedupeKey("OVERDUE:OWNER:" + candidate.getPaymentId() + ":" + managerId)
                    .deliveryChannels(List.of("IN_APP", "PUSH"))
                    .build());
        }
    }

    private static String overdueStage(int daysOverdue) {
        if (daysOverdue >= 7) {
            return "DAY7";
        }
        if (daysOverdue >= 3) {
            return "DAY3";
        }
        return "FIRST";
    }

    private static String paymentTypeLabel(SpacePaymentType type) {
        if (type == null) {
            return "payment";
        }
        return switch (type) {
            case RENT -> "rent";
            case DEPOSIT -> "security deposit";
            case UTILITY -> "utility";
            case MAINTENANCE -> "maintenance";
            case MEAL -> "meal";
            case SERVICE -> "service";
            case PENALTY -> "penalty";
            default -> "payment";
        };
    }

    private static String formatInr(BigDecimal amount, String currency) {
        BigDecimal value = amount != null ? amount : BigDecimal.ZERO;
        DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        format.setGroupingUsed(true);
        String text = format.format(value);
        if (currency == null || currency.isBlank() || "INR".equalsIgnoreCase(currency.trim())) {
            return "₹" + text;
        }
        return currency.trim() + " " + text;
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
                    .deliveryChannels(List.of("IN_APP", "PUSH"))
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
            LocalDate businessDate,
            UUID recipientUserId) {
        return PaymentReminderMessage.builder()
                .paymentId(candidate.getPaymentId())
                .spaceId(space.getId())
                .spaceName(space.getName())
                .memberId(candidate.getMemberId())
                .recipientUserId(recipientUserId)
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

    /**
     * Explains a calculator rejection. Does not change who is eligible.
     * The first matching rule is the one the owner sees.
     */
    private static String reminderIneligibilityReason(
            SpacePaymentEntity payment, PaymentDueStatusCalculator.DueStatus due) {
        if (payment.getMember() == null) {
            return "no active member";
        }
        if (!payment.getMember().isActive()) {
            return "member is inactive";
        }
        if (!PaymentDueStatusCalculator.REMINDER_ELIGIBLE_STATUSES.contains(payment.getPaymentStatus())) {
            return "status is " + payment.getPaymentStatus();
        }
        if (due.getOutstandingAmount() == null || due.getOutstandingAmount().signum() <= 0) {
            return "nothing is outstanding";
        }
        if (payment.getDueDate() == null
                || due.getBusinessDate() == null
                || payment.getDueDate().isAfter(
                        due.getBusinessDate().plusDays(PaymentDueStatusCalculator.REMINDER_APPROACHING_DAYS))) {
            return "due date is outside the reminder window";
        }
        return "not eligible";
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
                .providerConfigured(entity.getChannel() == ReminderChannel.PUSH
                        ? fcmProvider.isConfigured()
                        : whatsAppProvider.isConfigured())
                .retryable(isRetryableFailure(failureCode) && entity.getDeliveryStatus() == ReminderDeliveryStatus.FAILED)
                .sentAt(entity.getSentAt())
                .lastAttemptAt(entity.getLastAttemptAt())
                .attemptCount(entity.getAttemptCount())
                .build();
    }

    private static boolean isPermanentFailure(String failureReason) {
        String code = extractFailureCode(failureReason);
        return "MEMBER_NOT_ON_ACOMI".equals(code)
                || "INVALID_RECIPIENT".equals(code)
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

    private ReminderChannel activeChannel() {
        return whatsAppProvider.isConfigured() ? ReminderChannel.WHATSAPP : ReminderChannel.PUSH;
    }

    private boolean activeProviderConfigured() {
        return activeChannel() == ReminderChannel.WHATSAPP
                ? whatsAppProvider.isConfigured()
                : fcmProvider.isConfigured();
    }

    private String activeProviderMode() {
        return activeChannel() == ReminderChannel.WHATSAPP ? whatsAppProvider.getMode() : "fcm";
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
