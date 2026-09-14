package com.acomi.acomi_backend.mail.application.job;

import com.acomi.acomi_backend.mail.application.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retries FAILED/PENDING outbound emails. Does not resend SENT or SKIPPED rows.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailRetryJob {

    private final EmailService emailService;

    @Scheduled(fixedDelayString = "${acomi.mail.retry.job-delay-ms:300000}")
    public void retryDueEmails() {
        try {
            int attempted = emailService.retryDue();
            if (attempted > 0) {
                log.info("Email retry job attempted {} send(s)", attempted);
            }
        } catch (Exception ex) {
            log.error("Email retry job failed", ex);
        }
    }
}
