package com.acomi.acomi_backend.mail.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.acomi.acomi_backend.mail.application.dto.MailSendResult;
import com.acomi.acomi_backend.mail.application.dto.OutboundEmail;
import com.acomi.acomi_backend.mail.application.dto.SendEmailCommand;
import com.acomi.acomi_backend.mail.application.port.EmailPayloadComposer;
import com.acomi.acomi_backend.mail.application.port.MailTransport;
import com.acomi.acomi_backend.mail.application.support.EmailIdempotencyKeys;
import com.acomi.acomi_backend.mail.config.MailProperties;
import com.acomi.acomi_backend.mail.domain.model.EmailEventType;
import com.acomi.acomi_backend.mail.domain.model.EmailSendStatus;
import com.acomi.acomi_backend.mail.infrastructure.persistence.entity.EmailSendLogEntity;
import com.acomi.acomi_backend.mail.infrastructure.persistence.repository.EmailSendLogRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailServiceTest {

    @Mock
    private EmailSendLogRepository sendLogRepository;

    @Mock
    private MailTransport mailTransport;

    @Mock
    private EmailPayloadComposer composer;

    private final Map<UUID, EmailSendLogEntity> store = new HashMap<>();
    private final Map<String, UUID> keys = new HashMap<>();
    private EmailService emailService;
    private MailProperties mailProperties;
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-09T06:00:00Z"), ZoneId.of("Asia/Kolkata"));
    private UUID enquiryId;

    @BeforeEach
    void setUp() {
        store.clear();
        keys.clear();
        enquiryId = UUID.randomUUID();
        mailProperties = new MailProperties();
        mailProperties.setEnabled(true);
        mailProperties.setFrom("support@acomi.in");
        mailProperties.setFromName("ACOMI Support");
        mailProperties.setReplyTo("support@acomi.in");
        mailProperties.getRetry().setMaxAttempts(3);
        mailProperties.getRetry().setDelayMs(0);

        when(sendLogRepository.save(any())).thenAnswer(invocation -> {
            EmailSendLogEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            store.put(entity.getId(), entity);
            keys.put(entity.getIdempotencyKey(), entity.getId());
            return entity;
        });
        when(sendLogRepository.findByIdempotencyKey(any())).thenAnswer(invocation -> {
            UUID id = keys.get(invocation.getArgument(0));
            return Optional.ofNullable(id == null ? null : store.get(id));
        });
        when(sendLogRepository.lockById(any())).thenAnswer(invocation -> Optional.ofNullable(store.get(invocation.getArgument(0))));
        when(sendLogRepository.findRetryCandidates(any(), anyInt(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Collection<EmailSendStatus> statuses = invocation.getArgument(0);
            int maxAttempts = invocation.getArgument(1);
            LocalDateTime retryAfter = invocation.getArgument(2);
            List<EmailSendLogEntity> matches = new ArrayList<>();
            for (EmailSendLogEntity row : store.values()) {
                if (!statuses.contains(row.getStatus())) {
                    continue;
                }
                if (row.getAttemptCount() >= maxAttempts) {
                    continue;
                }
                if (row.getLastAttemptAt() != null && row.getLastAttemptAt().isAfter(retryAfter)) {
                    continue;
                }
                matches.add(row);
            }
            return matches;
        });

        emailService = new EmailService(
                sendLogRepository,
                mailTransport,
                mailProperties,
                new TransactionTemplate(new NoopTransactionManager()),
                clock,
                List.of(composer));
    }

    @Test
    void successfulSendCreatesSentAuditWithoutPersistingBody() {
        when(mailTransport.send(any())).thenReturn(MailSendResult.sent("<id@acomi.in>"));

        emailService.send(shareCommand("ketan@example.com", "Owner contact\nMobile: 9991110001\n"));

        EmailSendLogEntity row = onlyRow();
        assertThat(row.getStatus()).isEqualTo(EmailSendStatus.SENT);
        assertThat(row.getFromEmail()).isEqualTo("support@acomi.in");
        assertThat(row.getReplyTo()).isEqualTo("support@acomi.in");
        assertThat(row.getRecipientEmail()).isEqualTo("ketan@example.com");
        assertThat(row.getProviderMessageId()).isEqualTo("<id@acomi.in>");
        assertThat(row.getAttemptCount()).isEqualTo(1);
        assertThat(row.getSubject()).doesNotContain("9991110001");
        assertThat(row.getLastError()).isNull();
        verify(mailTransport, times(1)).send(any());
    }

    @Test
    void failedSendIsFailedNotSent() {
        when(mailTransport.send(any())).thenReturn(MailSendResult.failed("password=smtp-secret refused"));

        emailService.send(shareCommand("ketan@example.com", "Owner contact\nMobile: 9991110001\n"));

        EmailSendLogEntity row = onlyRow();
        assertThat(row.getStatus()).isEqualTo(EmailSendStatus.FAILED);
        assertThat(row.getSentAt()).isNull();
        assertThat(row.getFailedAt()).isNotNull();
        assertThat(row.getLastError()).doesNotContain("smtp-secret");
        assertThat(row.getLastError()).contains("password=***");
    }

    @Test
    void disabledTransportIsSkippedNotSent() {
        when(mailTransport.send(any())).thenReturn(MailSendResult.disabled());

        emailService.send(shareCommand("ketan@example.com", "Owner contact\nMobile: 9991110001\n"));

        EmailSendLogEntity row = onlyRow();
        assertThat(row.getStatus()).isEqualTo(EmailSendStatus.SKIPPED);
        assertThat(row.getSentAt()).isNull();
        verify(mailTransport, times(1)).send(any());
    }

    @Test
    void sameIdempotencyKeyDoesNotSendDuplicate() {
        when(mailTransport.send(any())).thenReturn(MailSendResult.sent("<id@acomi.in>"));
        SendEmailCommand command = shareCommand("ketan@example.com", "body");

        emailService.send(command);
        emailService.send(command);

        assertThat(store).hasSize(1);
        assertThat(onlyRow().getStatus()).isEqualTo(EmailSendStatus.SENT);
        verify(mailTransport, times(1)).send(any());
    }

    @Test
    void differentEventKeysForSameEnquiryAreAllowed() {
        when(mailTransport.send(any())).thenReturn(MailSendResult.sent("<id@acomi.in>"));

        emailService.send(shareCommand("ketan@example.com", "shared body"));
        emailService.send(SendEmailCommand.builder()
                .eventType(EmailEventType.ENQUIRY_SUBMITTED)
                .recipientEmail("ketan@example.com")
                .subject("ACOMI – Enquiry submitted for Sunrise PG")
                .plainBody("confirmation")
                .relatedEntityType(EmailService.RELATED_SPACE_ENQUIRY)
                .relatedEntityId(enquiryId)
                .idempotencyKey(EmailIdempotencyKeys.enquirySubmitted(enquiryId))
                .build());

        assertThat(store).hasSize(2);
        verify(mailTransport, times(2)).send(any());
    }

    @Test
    void retryDoesNotResendSuccessfulEmail() {
        when(mailTransport.send(any())).thenReturn(MailSendResult.sent("<id@acomi.in>"));
        emailService.send(shareCommand("ketan@example.com", "body"));

        int attempted = emailService.retryDue();

        assertThat(attempted).isZero();
        verify(mailTransport, times(1)).send(any());
        assertThat(onlyRow().getStatus()).isEqualTo(EmailSendStatus.SENT);
    }

    @Test
    void retrySendsFailedEmailOnceUntilSuccess() {
        when(mailTransport.send(any()))
                .thenReturn(MailSendResult.failed("transient"))
                .thenReturn(MailSendResult.sent("<retry@acomi.in>"));
        when(composer.supports(EmailEventType.ENQUIRY_SHARED)).thenReturn(true);
        when(composer.compose(any())).thenReturn(Optional.of(new OutboundEmail(
                "ketan@example.com",
                "ACOMI – Contact details for Sunrise PG",
                "Owner contact\nMobile: 9991110001\n",
                "support@acomi.in",
                "ACOMI Support",
                "support@acomi.in")));

        emailService.send(shareCommand("ketan@example.com", "Owner contact\nMobile: 9991110001\n"));
        assertThat(onlyRow().getStatus()).isEqualTo(EmailSendStatus.FAILED);

        emailService.retryDue();

        assertThat(onlyRow().getStatus()).isEqualTo(EmailSendStatus.SENT);
        assertThat(onlyRow().getAttemptCount()).isEqualTo(2);
        verify(mailTransport, times(2)).send(any());

        emailService.retryDue();
        verify(mailTransport, times(2)).send(any());
    }

    @Test
    void logsDoNotContainOwnerContactOrSmtpPassword() {
        Logger logger = (Logger) LoggerFactory.getLogger(EmailService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        when(mailTransport.send(any())).thenReturn(MailSendResult.failed("password=smtp-secret"));

        emailService.send(shareCommand("ketan@example.com", "Owner contact\nMobile: 9991110001\n"));

        String joined = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(joined).doesNotContain("9991110001");
        assertThat(joined).doesNotContain("smtp-secret");
        assertThat(joined).doesNotContain("Owner contact");
        logger.detachAppender(appender);
    }

    @Test
    void outboundUsesConfiguredSupportAddresses() {
        when(mailTransport.send(any())).thenReturn(MailSendResult.sent("<id@acomi.in>"));

        emailService.send(shareCommand("ketan@example.com", "body"));

        org.mockito.ArgumentCaptor<OutboundEmail> captor = org.mockito.ArgumentCaptor.forClass(OutboundEmail.class);
        verify(mailTransport).send(captor.capture());
        assertThat(captor.getValue().from()).isEqualTo("support@acomi.in");
        assertThat(captor.getValue().fromName()).isEqualTo("ACOMI Support");
        assertThat(captor.getValue().replyTo()).isEqualTo("support@acomi.in");
        assertThat(captor.getValue().to()).isEqualTo("ketan@example.com");
    }

    private SendEmailCommand shareCommand(String to, String body) {
        return SendEmailCommand.builder()
                .eventType(EmailEventType.ENQUIRY_SHARED)
                .recipientUserId(UUID.randomUUID())
                .recipientEmail(to)
                .subject("ACOMI – Contact details for Sunrise PG")
                .plainBody(body)
                .relatedEntityType(EmailService.RELATED_SPACE_ENQUIRY)
                .relatedEntityId(enquiryId)
                .idempotencyKey("ENQUIRY_SHARED:" + enquiryId)
                .build();
    }

    private EmailSendLogEntity onlyRow() {
        assertThat(store).hasSize(1);
        return store.values().iterator().next();
    }

    private static final class NoopTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
    }
}
