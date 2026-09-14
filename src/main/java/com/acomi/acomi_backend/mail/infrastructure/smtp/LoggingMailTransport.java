package com.acomi.acomi_backend.mail.infrastructure.smtp;

import com.acomi.acomi_backend.mail.application.dto.MailSendResult;
import com.acomi.acomi_backend.mail.application.dto.OutboundEmail;
import com.acomi.acomi_backend.mail.application.port.MailTransport;
import com.acomi.acomi_backend.mail.application.support.EmailTextSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Local/dev transport. Does not open SMTP and must never be recorded as SENT.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "acomi.mail.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingMailTransport implements MailTransport {

    @Override
    public boolean isLive() {
        return false;
    }

    @Override
    public MailSendResult send(OutboundEmail email) {
        log.info(
                "Email skipped (SMTP disabled) to={} subjectPresent={}",
                EmailTextSanitizer.header(email.to()),
                email.subject() != null && !email.subject().isBlank());
        return MailSendResult.disabled();
    }
}
