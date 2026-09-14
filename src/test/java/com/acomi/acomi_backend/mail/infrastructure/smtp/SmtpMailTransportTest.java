package com.acomi.acomi_backend.mail.infrastructure.smtp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.acomi.acomi_backend.mail.application.dto.MailSendResult;
import com.acomi.acomi_backend.mail.application.dto.OutboundEmail;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpMailTransportTest {

    @Test
    void setsSupportFromAndReplyTo() throws Exception {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        MimeMessage mime = new MimeMessage((jakarta.mail.Session) null);
        when(javaMailSender.createMimeMessage()).thenReturn(mime);
        doAnswer(invocation -> {
            MimeMessage sent = invocation.getArgument(0);
            sent.setHeader("Message-ID", "<test-message-id@acomi.in>");
            return null;
        })
                .when(javaMailSender)
                .send(any(MimeMessage.class));

        SmtpMailTransport transport = new SmtpMailTransport(javaMailSender);
        MailSendResult result = transport.send(new OutboundEmail(
                "ketan@example.com",
                "ACOMI – Contact details for Sunrise PG",
                "Owner contact\nMobile: 9991110001\n",
                "support@acomi.in",
                "ACOMI Support",
                "support@acomi.in"));

        assertThat(result.success()).isTrue();
        assertThat(result.skipped()).isFalse();
        InternetAddress from = (InternetAddress) mime.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("support@acomi.in");
        assertThat(from.getPersonal()).isEqualTo("ACOMI Support");
        InternetAddress replyTo = (InternetAddress) mime.getReplyTo()[0];
        assertThat(replyTo.getAddress()).isEqualTo("support@acomi.in");
        assertThat(mime.getSubject()).isEqualTo("ACOMI – Contact details for Sunrise PG");
        assertThat(mime.getAllRecipients()[0].toString()).isEqualTo("ketan@example.com");
    }

    @Test
    void sendsHtmlAlternativeWhenPresent() throws Exception {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        MimeMessage mime = new MimeMessage((jakarta.mail.Session) null);
        when(javaMailSender.createMimeMessage()).thenReturn(mime);
        doAnswer(invocation -> {
            MimeMessage sent = invocation.getArgument(0);
            sent.setHeader("Message-ID", "<html-message-id@acomi.in>");
            return null;
        })
                .when(javaMailSender)
                .send(any(MimeMessage.class));

        MailSendResult result = new SmtpMailTransport(javaMailSender)
                .send(new OutboundEmail(
                        "ketan@example.com",
                        "ACOMI – Contact details for Sunrise PG",
                        "Owner contact\nMobile: 9991110001\n",
                        "support@acomi.in",
                        "ACOMI Support",
                        "support@acomi.in",
                        "<html><body>Owner contact</body></html>"));

        assertThat(result.success()).isTrue();
        assertThat(mime.getContent()).isInstanceOf(jakarta.mail.internet.MimeMultipart.class);
    }

    @Test
    void smtpFailureIsNotSuccessAndDoesNotLogPassword() {
        Logger logger = (Logger) LoggerFactory.getLogger(SmtpMailTransport.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        MimeMessage mime = new MimeMessage((jakarta.mail.Session) null);
        when(javaMailSender.createMimeMessage()).thenReturn(mime);
        org.mockito.Mockito.doThrow(new RuntimeException("password=should-not-leak"))
                .when(javaMailSender)
                .send(any(MimeMessage.class));

        MailSendResult result = new SmtpMailTransport(javaMailSender)
                .send(new OutboundEmail(
                        "ketan@example.com",
                        "ACOMI – Contact details for Sunrise PG",
                        "body",
                        "support@acomi.in",
                        "ACOMI Support",
                        "support@acomi.in"));

        assertThat(result.success()).isFalse();
        assertThat(result.skipped()).isFalse();
        assertThat(result.failureReason()).doesNotContain("should-not-leak");
        assertThat(result.failureReason()).contains("password=***");
        String joined = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(joined).doesNotContain("should-not-leak");
        logger.detachAppender(appender);
    }
}
