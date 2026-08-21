package com.acomi.acomi_backend.auth.application.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.acomi.acomi_backend.auth.domain.model.OtpPurpose;
import com.acomi.acomi_backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

class OtpSenderTest {

    @Test
    void devSender_logsGeneratedOtpWithoutFullMobile() {
        Logger logger = (Logger) LoggerFactory.getLogger(DevOtpSender.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        new DevOtpSender().send("9876543210", "482731", OtpPurpose.REGISTER);

        assertThat(appender.list).hasSize(1);
        String message = appender.list.get(0).getFormattedMessage();
        assertThat(message).contains("[DEV OTP]");
        assertThat(message).contains("purpose=REGISTER");
        assertThat(message).contains("otp=482731");
        assertThat(message).contains("+91******3210");
        assertThat(message).doesNotContain("9876543210");
        logger.detachAppender(appender);
    }

    @Test
    void noneSender_doesNotLeakOtpAndFailsClosed() {
        Logger logger = (Logger) LoggerFactory.getLogger(NoneOtpSender.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        assertThatThrownBy(() -> new NoneOtpSender().send("9876543210", "482731", OtpPurpose.REGISTER))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Unable to send OTP. Please try again later.")
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        assertThat(appender.list.get(0).getFormattedMessage()).doesNotContain("482731");
        logger.detachAppender(appender);
    }

    @Test
    void senderConfiguration_rejectsUnknownProvider() {
        com.acomi.acomi_backend.config.security.OtpProperties properties =
                new com.acomi.acomi_backend.config.security.OtpProperties();
        properties.setSender("twilio");

        assertThatThrownBy(() -> new OtpSenderConfiguration().otpSender(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported acomi.otp.sender");
    }

    @Test
    void senderConfiguration_devUsesDevSenderAndNoneUsesNoneSender() {
        com.acomi.acomi_backend.config.security.OtpProperties properties =
                new com.acomi.acomi_backend.config.security.OtpProperties();
        OtpSenderConfiguration configuration = new OtpSenderConfiguration();

        properties.setSender("dev");
        assertThat(configuration.otpSender(properties)).isInstanceOf(DevOtpSender.class);

        properties.setSender("none");
        assertThat(configuration.otpSender(properties)).isInstanceOf(NoneOtpSender.class);
    }
}
