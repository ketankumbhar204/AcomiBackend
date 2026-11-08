package com.acomi.acomi_backend.payment.application.job;

import com.acomi.acomi_backend.payment.application.service.SpacePaymentGenerationService;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Closes the previous calendar month for OPEN_ENDED daily-rate rent generation.
 * Safe to retry — upsert is idempotent. Does not create rent for the open current month.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpenEndedRentMonthCloseScheduler {

    private final SpaceRepository spaceRepository;
    private final SpacePaymentGenerationService paymentGenerationService;

    @Value("${acomi.billing.open-ended-month-close.enabled:true}")
    private boolean enabled;

    /**
     * 00:40 on the 1st through the 3rd, Asia/Kolkata. Days 2 and 3 retry a missed
     * 1st. The rent gate still refuses the open current month.
     */
    @Scheduled(
            cron = "${acomi.billing.open-ended-month-close.cron:0 40 0 1-3 * *}",
            zone = "${acomi.billing.open-ended-month-close.zone:Asia/Kolkata}")
    public void closePreviousMonthOpenEndedRent() {
        if (!enabled) {
            return;
        }
        List<SpaceEntity> spaces = spaceRepository.findByIsActiveTrue();
        log.info("OPEN_ENDED month-close started activeSpaces={}", spaces.size());

        int ok = 0;
        int failed = 0;
        for (SpaceEntity space : spaces) {
            if (!isAccommodation(space.getType())) {
                continue;
            }
            YearMonth closedMonth = YearMonth.now(zoneOf(space)).minusMonths(1);
            try {
                // System path — no owner membership required; rent gate skips open months.
                paymentGenerationService.syncExpectedPaymentsInternal(space.getId(), closedMonth);
                ok++;
            } catch (Exception ex) {
                failed++;
                log.error(
                        "OPEN_ENDED month-close failed space={} month={}",
                        space.getId(),
                        closedMonth,
                        ex);
            }
        }
        log.info("OPEN_ENDED month-close finished spacesOk={} spacesFailed={}", ok, failed);
    }

    private static ZoneId zoneOf(SpaceEntity space) {
        String raw = space.getTimezone();
        if (raw == null || raw.isBlank()) {
            return ZoneId.of("Asia/Kolkata");
        }
        try {
            return ZoneId.of(raw.trim());
        } catch (DateTimeException ex) {
            return ZoneId.of("Asia/Kolkata");
        }
    }

    private static boolean isAccommodation(SpaceType type) {
        return type == SpaceType.PG
                || type == SpaceType.HOSTEL
                || type == SpaceType.CO_LIVING
                || type == SpaceType.RENTAL
                || type == SpaceType.CORPORATE_STAY;
    }
}
