package com.acomi.acomi_backend.payment.application.job;

import com.acomi.acomi_backend.payment.api.dto.response.PaymentReminderProcessResultResponse;
import com.acomi.acomi_backend.payment.application.service.PaymentReminderService;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily overdue payment reminder processing.
 * Eligibility and delivery are delegated — this job does not calculate billing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReminderScheduler {

    private final SpaceRepository spaceRepository;
    private final PaymentReminderService paymentReminderService;

    @Value("${acomi.reminders.scheduler.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${acomi.reminders.scheduler.cron:0 30 9 * * *}")
    public void processDailyReminders() {
        if (!enabled) {
            return;
        }
        List<SpaceEntity> spaces = spaceRepository.findByIsActiveTrue();
        log.info("Payment reminder scheduler started activeSpaces={}", spaces.size());

        int totalCandidates = 0;
        int totalCreated = 0;
        int totalSkipped = 0;
        int totalSuccess = 0;
        int totalFailed = 0;

        for (SpaceEntity space : spaces) {
            try {
                PaymentReminderProcessResultResponse result =
                        paymentReminderService.processSpaceAsSystem(space.getId());
                totalCandidates += result.getCandidatesFound();
                totalCreated += result.getRemindersCreated();
                totalSkipped += result.getRemindersSkippedDuplicate();
                totalSuccess += result.getDeliverySuccesses();
                totalFailed += result.getDeliveryFailures();
            } catch (Exception ex) {
                log.error("Payment reminder scheduler failed for space={}", space.getId(), ex);
            }
        }

        log.info(
                "Payment reminder scheduler finished spaces={} candidates={} created={} skipped={} "
                        + "successes={} failures={}",
                spaces.size(),
                totalCandidates,
                totalCreated,
                totalSkipped,
                totalSuccess,
                totalFailed);
    }
}
