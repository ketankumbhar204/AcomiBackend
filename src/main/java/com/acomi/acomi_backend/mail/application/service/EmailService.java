package com.acomi.acomi_backend.mail.application.service;

import com.acomi.acomi_backend.mail.application.dto.MailSendResult;
import com.acomi.acomi_backend.mail.application.dto.OutboundEmail;
import com.acomi.acomi_backend.mail.application.dto.SendEmailCommand;
import com.acomi.acomi_backend.mail.application.port.EmailPayloadComposer;
import com.acomi.acomi_backend.mail.application.port.MailTransport;
import com.acomi.acomi_backend.mail.application.support.EmailErrorSanitizer;
import com.acomi.acomi_backend.mail.application.support.EmailTextSanitizer;
import com.acomi.acomi_backend.mail.config.MailProperties;
import com.acomi.acomi_backend.mail.domain.model.EmailSendStatus;
import com.acomi.acomi_backend.mail.infrastructure.persistence.entity.EmailSendLogEntity;
import com.acomi.acomi_backend.mail.infrastructure.persistence.repository.EmailSendLogRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Generic outbound email orchestration: persist intent, commit, then send.
 *
 * <p>Bodies are held in memory for the first attempt only and are never written
 * to {@code email_send_logs}. Retry rebuilds the payload via {@link EmailPayloadComposer}.
 */
@Service
@Slf4j
public class EmailService {

    public static final String RELATED_SPACE_ENQUIRY = "SPACE_ENQUIRY";

    private final EmailSendLogRepository sendLogRepository;
    private final MailTransport mailTransport;
    private final MailProperties mailProperties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final List<EmailPayloadComposer> composers;
    private final Map<UUID, OutboundEmail> pendingPayloads = new ConcurrentHashMap<>();

    public EmailService(
            EmailSendLogRepository sendLogRepository,
            MailTransport mailTransport,
            MailProperties mailProperties,
            TransactionTemplate transactionTemplate,
            Clock clock,
            List<EmailPayloadComposer> composers) {
        this.sendLogRepository = sendLogRepository;
        this.mailTransport = mailTransport;
        this.mailProperties = mailProperties;
        this.transactionTemplate = requiresNew(transactionTemplate);
        this.clock = clock;
        this.composers = composers == null ? List.of() : composers;
    }

    /**
     * Records an email intent in the current transaction and sends after commit.
     * Already {@link EmailSendStatus#SENT} or {@link EmailSendStatus#SKIPPED} keys are no-ops.
     */
    public void send(SendEmailCommand command) {
        Claim claim = persistIntent(command);
        if (claim.log() == null || !claim.shouldDeliver()) {
            return;
        }
        OutboundEmail payload = toOutbound(command);
        pendingPayloads.put(claim.log().getId(), payload);
        runAfterCommit(() -> deliver(claim.log().getId()));
    }

    /** Scheduler entry — retries FAILED/PENDING rows that are due. */
    public int retryDue() {
        LocalDateTime now = LocalDateTime.now(clock);
        long delayMs = mailProperties.getRetry() != null ? mailProperties.getRetry().getDelayMs() : 60_000;
        int maxAttempts = maxAttempts();
        LocalDateTime retryAfter = now.minus(Duration.ofMillis(Math.max(0, delayMs)));
        List<EmailSendLogEntity> candidates = sendLogRepository.findRetryCandidates(
                EnumSet.of(EmailSendStatus.PENDING, EmailSendStatus.FAILED, EmailSendStatus.SENDING),
                maxAttempts,
                retryAfter);
        int attempted = 0;
        for (EmailSendLogEntity candidate : candidates) {
            try {
                deliver(candidate.getId());
                attempted++;
            } catch (Exception ex) {
                log.warn(
                        "Email retry skipped logId={} eventType={} reason={}",
                        candidate.getId(),
                        candidate.getEventType(),
                        EmailErrorSanitizer.sanitize(ex));
            }
        }
        return attempted;
    }

    private Claim persistIntent(SendEmailCommand command) {
        validate(command);
        String key = command.getIdempotencyKey().trim();
        Optional<EmailSendLogEntity> existing = sendLogRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return claimExisting(existing.get());
        }
        EmailSendLogEntity created = EmailSendLogEntity.builder()
                .eventType(command.getEventType())
                .recipientUserId(command.getRecipientUserId())
                .recipientEmail(EmailTextSanitizer.header(command.getRecipientEmail()))
                .fromEmail(requiredFrom())
                .replyTo(resolvedReplyTo())
                .subject(EmailTextSanitizer.subject(command.getSubject()))
                .relatedEntityType(command.getRelatedEntityType())
                .relatedEntityId(command.getRelatedEntityId())
                .idempotencyKey(key)
                .status(EmailSendStatus.PENDING)
                .attemptCount(0)
                .build();
        try {
            return new Claim(sendLogRepository.save(created), true);
        } catch (DataIntegrityViolationException ex) {
            return sendLogRepository
                    .findByIdempotencyKey(key)
                    .map(this::claimExisting)
                    .orElseThrow(() -> ex);
        }
    }

    private Claim claimExisting(EmailSendLogEntity existing) {
        if (existing.getStatus() == EmailSendStatus.SENT || existing.getStatus() == EmailSendStatus.SKIPPED) {
            log.info(
                    "Email already completed eventType={} entityId={} status={} idempotencyKey={}",
                    existing.getEventType(),
                    existing.getRelatedEntityId(),
                    existing.getStatus(),
                    existing.getIdempotencyKey());
            return new Claim(existing, false);
        }
        if (existing.getAttemptCount() >= maxAttempts() && existing.getStatus() == EmailSendStatus.FAILED) {
            log.info(
                    "Email retries exhausted eventType={} entityId={} attempts={}",
                    existing.getEventType(),
                    existing.getRelatedEntityId(),
                    existing.getAttemptCount());
            return new Claim(existing, false);
        }
        return new Claim(existing, true);
    }

    private void deliver(UUID logId) {
        ClaimedSend claimed;
        try {
            claimed = transactionTemplate.execute(status -> claimForSend(logId));
        } catch (Exception ex) {
            log.warn("Email claim failed logId={} reason={}", logId, EmailErrorSanitizer.sanitize(ex));
            return;
        }
        if (claimed == null || claimed == ClaimedSend.SKIP) {
            pendingPayloads.remove(logId);
            return;
        }

        OutboundEmail payload = pendingPayloads.get(logId);
        if (payload == null) {
            payload = composeForRetry(claimed.log());
        }
        if (payload == null) {
            markPermanentFailure(logId, "EMAIL_PAYLOAD_UNAVAILABLE");
            pendingPayloads.remove(logId);
            return;
        }

        MailSendResult result;
        try {
            result = mailTransport.send(payload);
        } catch (Exception ex) {
            result = MailSendResult.failed(EmailErrorSanitizer.sanitize(ex));
        }

        final MailSendResult delivery = result;
        transactionTemplate.executeWithoutResult(status -> applyResult(logId, delivery));
        pendingPayloads.remove(logId);
    }

    private ClaimedSend claimForSend(UUID logId) {
        EmailSendLogEntity row = sendLogRepository.lockById(logId).orElse(null);
        if (row == null) {
            return ClaimedSend.SKIP;
        }
        if (row.getStatus() == EmailSendStatus.SENT || row.getStatus() == EmailSendStatus.SKIPPED) {
            return ClaimedSend.SKIP;
        }
        if (row.getAttemptCount() >= maxAttempts() && row.getStatus() == EmailSendStatus.FAILED) {
            return ClaimedSend.SKIP;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (row.getStatus() == EmailSendStatus.SENDING
                && row.getLastAttemptAt() != null
                && row.getLastAttemptAt().isAfter(now.minusSeconds(stuckSendingSeconds()))) {
            return ClaimedSend.SKIP;
        }
        row.setStatus(EmailSendStatus.SENDING);
        row.setAttemptCount(row.getAttemptCount() + 1);
        row.setLastAttemptAt(now);
        return new ClaimedSend(sendLogRepository.save(row));
    }

    private void applyResult(UUID logId, MailSendResult result) {
        EmailSendLogEntity row = sendLogRepository.lockById(logId).orElse(null);
        if (row == null) {
            return;
        }
        if (row.getStatus() == EmailSendStatus.SENT) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (result.skipped()) {
            row.setStatus(EmailSendStatus.SKIPPED);
            row.setLastError(null);
            row.setFailedAt(null);
            row.setSentAt(null);
            sendLogRepository.save(row);
            log.info(
                    "Email skipped (SMTP disabled) eventType={} entityId={} recipient={} attempt={}",
                    row.getEventType(),
                    row.getRelatedEntityId(),
                    row.getRecipientEmail(),
                    row.getAttemptCount());
            return;
        }
        if (result.success()) {
            row.setStatus(EmailSendStatus.SENT);
            row.setProviderMessageId(result.providerMessageId());
            row.setSentAt(now);
            row.setFailedAt(null);
            row.setLastError(null);
            sendLogRepository.save(row);
            log.info(
                    "Email sent eventType={} entityId={} recipient={} attempt={} messageId={}",
                    row.getEventType(),
                    row.getRelatedEntityId(),
                    row.getRecipientEmail(),
                    row.getAttemptCount(),
                    result.providerMessageId());
            return;
        }
        row.setStatus(EmailSendStatus.FAILED);
        row.setFailedAt(now);
        row.setLastError(EmailErrorSanitizer.sanitize(result.failureReason()));
        sendLogRepository.save(row);
        log.warn(
                "Email failed eventType={} entityId={} recipient={} attempt={} reason={}",
                row.getEventType(),
                row.getRelatedEntityId(),
                row.getRecipientEmail(),
                row.getAttemptCount(),
                row.getLastError());
    }

    private void markPermanentFailure(UUID logId, String reason) {
        transactionTemplate.executeWithoutResult(status -> {
            EmailSendLogEntity row = sendLogRepository.lockById(logId).orElse(null);
            if (row == null || row.getStatus() == EmailSendStatus.SENT) {
                return;
            }
            row.setStatus(EmailSendStatus.FAILED);
            row.setFailedAt(LocalDateTime.now(clock));
            row.setLastError(reason);
            row.setAttemptCount(Math.max(row.getAttemptCount(), maxAttempts()));
            sendLogRepository.save(row);
            log.warn(
                    "Email permanently failed eventType={} entityId={} reason={}",
                    row.getEventType(),
                    row.getRelatedEntityId(),
                    reason);
        });
    }

    private OutboundEmail composeForRetry(EmailSendLogEntity sendLog) {
        return composers.stream()
                .filter(composer -> composer.supports(sendLog.getEventType()))
                .findFirst()
                .flatMap(composer -> composer.compose(sendLog))
                .orElse(null);
    }

    private OutboundEmail toOutbound(SendEmailCommand command) {
        return new OutboundEmail(
                EmailTextSanitizer.header(command.getRecipientEmail()),
                EmailTextSanitizer.subject(command.getSubject()),
                command.getPlainBody() == null ? "" : command.getPlainBody(),
                requiredFrom(),
                EmailTextSanitizer.header(mailProperties.getFromName()),
                resolvedReplyTo(),
                command.getHtmlBody());
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) {
                        action.run();
                    }
                }
            });
            return;
        }
        action.run();
    }

    private static TransactionTemplate requiresNew(TransactionTemplate source) {
        TransactionTemplate isolated = new TransactionTemplate(source.getTransactionManager());
        isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return isolated;
    }

    private void validate(SendEmailCommand command) {
        if (command == null
                || command.getEventType() == null
                || command.getIdempotencyKey() == null
                || command.getIdempotencyKey().isBlank()
                || command.getRecipientEmail() == null
                || command.getRecipientEmail().isBlank()) {
            throw new IllegalArgumentException("Email command requires event type, recipient, and idempotency key");
        }
        requiredFrom();
    }

    private String requiredFrom() {
        String from = EmailTextSanitizer.header(mailProperties.getFrom());
        if (from.isBlank()) {
            throw new IllegalStateException("acomi.mail.from is not configured");
        }
        return from;
    }

    private String resolvedReplyTo() {
        return EmailTextSanitizer.header(mailProperties.resolvedReplyTo());
    }

    private int maxAttempts() {
        int configured = mailProperties.getRetry() != null ? mailProperties.getRetry().getMaxAttempts() : 3;
        return Math.max(1, configured);
    }

    private long stuckSendingSeconds() {
        long delayMs = mailProperties.getRetry() != null ? mailProperties.getRetry().getDelayMs() : 60_000;
        return Math.max(30, delayMs / 1000);
    }

    private record Claim(EmailSendLogEntity log, boolean shouldDeliver) {}

    private static final class ClaimedSend {
        static final ClaimedSend SKIP = new ClaimedSend(null);
        private final EmailSendLogEntity log;

        private ClaimedSend(EmailSendLogEntity log) {
            this.log = log;
        }

        private EmailSendLogEntity log() {
            return log;
        }
    }
}
