package com.acomi.acomi_backend.mail.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Local E2E fails loudly when SMTP is on but Titan credentials are missing from this process.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "acomi.mail.enabled", havingValue = "true")
public class MailStartupCheck {

    private final String username;
    private final String password;

    public MailStartupCheck(
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password) {
        this.username = username;
        this.password = password;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warnIfSmtpPasswordMissing() {
        if (!StringUtils.hasText(password)) {
            log.warn(
                    "Outbound SMTP is enabled but SPRING_MAIL_PASSWORD is empty in this process. "
                            + "Sends will fail with MailAuthenticationException. Set the Titan password "
                            + "in the same shell as spring-boot:run (mailbox {}), then restart.",
                    StringUtils.hasText(username) ? username : "support@acomi.in");
        }
    }
}
