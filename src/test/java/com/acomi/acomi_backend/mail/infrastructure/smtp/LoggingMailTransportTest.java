package com.acomi.acomi_backend.mail.infrastructure.smtp;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.acomi.acomi_backend.mail.application.dto.MailSendResult;
import com.acomi.acomi_backend.mail.application.dto.OutboundEmail;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LoggingMailTransportTest {

    @Test
    void disabledTransportSkipsAndDoesNotLogOwnerContact() {
        Logger logger = (Logger) LoggerFactory.getLogger(LoggingMailTransport.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        MailSendResult result = new LoggingMailTransport()
                .send(new OutboundEmail(
                        "ketan@example.com",
                        "ACOMI – Contact details for Sunrise PG",
                        "Owner contact\nMobile: 9991110001\nAlternate mobile: 9991110002\n",
                        "support@acomi.in",
                        "ACOMI Support",
                        "support@acomi.in"));

        assertThat(result.skipped()).isTrue();
        assertThat(result.success()).isFalse();
        assertThat(new LoggingMailTransport().isLive()).isFalse();
        String joined = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(joined).doesNotContain("9991110001");
        assertThat(joined).doesNotContain("9991110002");
        assertThat(joined).doesNotContain("Owner contact");
        assertThat(joined).contains("SMTP disabled");
        logger.detachAppender(appender);
    }
}
