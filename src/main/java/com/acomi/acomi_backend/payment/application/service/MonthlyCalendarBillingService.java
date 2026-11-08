package com.acomi.acomi_backend.payment.application.service;

import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import com.acomi.acomi_backend.payment.application.service.MonthlyRentMemberBilling.Result;
import com.acomi.acomi_backend.payment.application.support.MessCalendarBillingPolicy;
import com.acomi.acomi_backend.payment.domain.model.MonthlyBillingRunStatus;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.MonthlyBillingControlEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.MonthlyBillingRunEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.MonthlyBillingControlRepository;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.MonthlyBillingRunRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Creates the current calendar month's accommodation rent for every active space.
 * Mess/Tiffin customers are recorded as not eligible: that product bills by pack
 * purchase or by meal, not by a calendar-month subscription payment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyCalendarBillingService {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final short CONTROL_ID = 1;

    private final SpaceRepository spaceRepository;
    private final OccupancyRepository occupancyRepository;
    private final MonthlyBillingControlRepository controlRepository;
    private final MonthlyBillingRunRepository runRepository;
    private final MonthlyRentMemberBilling memberBilling;

    @Value("${acomi.billing.monthly-rent.enabled:true}")
    private boolean enabled;

    @Value("${acomi.billing.monthly-rent.recovery-months:2}")
    private int recoveryMonths;

    public void runDueCycles() {
        if (!enabled) {
            return;
        }
        LocalDate enabledFrom = enabledFrom();
        int window = Math.max(recoveryMonths, 1);
        List<SpaceEntity> spaces = spaceRepository.findByIsActiveTrue();
        log.info("Monthly billing cycle started spaces={} enabledFrom={}", spaces.size(), enabledFrom);
        for (SpaceEntity space : spaces) {
            ZoneId zone = zoneOf(space);
            YearMonth current = YearMonth.from(LocalDate.now(zone));
            YearMonth windowStart = current.minusMonths(window - 1L);
            YearMonth from = YearMonth.from(enabledFrom);
            if (from.isBefore(windowStart)) {
                from = windowStart;
            }
            Set<YearMonth> months = new LinkedHashSet<>();
            for (YearMonth month = from; !month.isAfter(current); month = month.plusMonths(1)) {
                if (!month.atDay(1).isBefore(enabledFrom)) {
                    months.add(month);
                }
            }
            for (MonthlyBillingRunEntity failed : runRepository.findBySpaceIdAndStatusIn(
                    space.getId(), EnumSet.of(MonthlyBillingRunStatus.FAILED, MonthlyBillingRunStatus.PARTIAL))) {
                YearMonth month = YearMonth.from(failed.getBillingMonth());
                if (!month.atDay(1).isBefore(enabledFrom) && !month.isAfter(current)) {
                    months.add(month);
                }
            }
            for (YearMonth month : months) {
                try {
                    processSpace(space, month, zone);
                } catch (Exception ex) {
                    log.error("Monthly billing failed space={} month={}", space.getId(), month, ex);
                }
            }
        }
    }

    public void processSpace(SpaceEntity space, YearMonth month, ZoneId zone) {
        boolean claimed;
        try {
            claimed = claim(space.getId(), month, zone.getId());
        } catch (DataIntegrityViolationException ex) {
            log.info("Monthly billing already claimed space={} month={}", space.getId(), month);
            return;
        }
        if (!claimed) {
            return;
        }
        try {
            Totals totals = isAccommodation(space.getType())
                    ? billAccommodation(space, month)
                    : messNotApplicable();
            finish(space.getId(), month, totals, null);
        } catch (Exception ex) {
            finish(space.getId(), month, null, ex);
            throw ex;
        }
    }

    private Totals billAccommodation(SpaceEntity space, YearMonth month) {
        LocalDate asOf = month.atDay(1);
        LocalDateTime monthStart = month.atDay(1).atStartOfDay();
        LocalDateTime monthEndExclusive = month.plusMonths(1).atDay(1).atStartOfDay();
        List<OccupancyEntity> occupancies = occupancyRepository.findBillableBySpaceIdForMonth(
                space.getId(), monthStart, monthEndExclusive, month.atEndOfMonth());
        Map<String, Integer> skips = new LinkedHashMap<>();
        int eligible = 0;
        int created = 0;
        int skipped = 0;
        int failed = 0;
        int queued = 0;
        for (OccupancyEntity occupancy : occupancies) {
            try {
                Result result = memberBilling.bill(occupancy.getId(), month, asOf);
                if ("CREATED".equals(result.outcome())) {
                    eligible++;
                    created++;
                    if (result.notificationQueued()) {
                        queued++;
                    }
                } else if ("ALREADY_PRESENT".equals(result.outcome())
                        || "CANCELLED_OR_REFUNDED".equals(result.outcome())) {
                    eligible++;
                    skipped++;
                    bump(skips, result.outcome());
                } else {
                    skipped++;
                    bump(skips, result.outcome());
                }
            } catch (DataIntegrityViolationException ex) {
                eligible++;
                skipped++;
                bump(skips, "ALREADY_PRESENT");
            } catch (Exception ex) {
                failed++;
                bump(skips, "FAILED");
                log.warn("Monthly rent failed space={} occupancy={}", space.getId(), occupancy.getId(), ex);
            }
        }
        return new Totals(eligible, 0, created, skipped, failed, queued, summarize(skips));
    }

    private static Totals messNotApplicable() {
        return new Totals(0, 0, 0, 1, 0, 0, MessCalendarBillingPolicy.SKIP_REASON
                + ": " + MessCalendarBillingPolicy.EXPLANATION);
    }

    public boolean claim(UUID spaceId, YearMonth month, String zone) {
        LocalDate key = month.atDay(1);
        MonthlyBillingRunEntity existing = runRepository.findBySpaceIdAndBillingMonth(spaceId, key).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            if (existing.getStatus() == MonthlyBillingRunStatus.SUCCESS) {
                return false;
            }
            if (existing.getStatus() == MonthlyBillingRunStatus.RUNNING
                    && existing.getStartedAt() != null
                    && existing.getStartedAt().isAfter(now.minusMinutes(20))) {
                return false;
            }
            existing.setStatus(MonthlyBillingRunStatus.RUNNING);
            existing.setStartedAt(now);
            existing.setFinishedAt(null);
            existing.setErrorMessage(null);
            existing.setBusinessTimezone(zone);
            runRepository.save(existing);
            return true;
        }
        runRepository.saveAndFlush(MonthlyBillingRunEntity.builder()
                .spaceId(spaceId)
                .billingMonth(key)
                .businessTimezone(zone)
                .status(MonthlyBillingRunStatus.RUNNING)
                .startedAt(now)
                .build());
        return true;
    }

    public void finish(UUID spaceId, YearMonth month, Totals totals, Exception error) {
        MonthlyBillingRunEntity run = runRepository
                .findBySpaceIdAndBillingMonth(spaceId, month.atDay(1))
                .orElse(null);
        if (run == null) {
            return;
        }
        run.setFinishedAt(LocalDateTime.now());
        if (error != null || totals == null) {
            run.setStatus(MonthlyBillingRunStatus.FAILED);
            run.setErrorMessage(trim(error == null ? "Unknown failure" : error.getMessage()));
        } else {
            run.setEligibleAccommodation(totals.eligibleAccommodation);
            run.setEligibleMess(totals.eligibleMess);
            run.setPaymentsCreated(totals.created);
            run.setPaymentsSkipped(totals.skipped);
            run.setPaymentsFailed(totals.failed);
            run.setNotificationsQueued(totals.queued);
            run.setSkipSummary(trimSummary(totals.skipSummary));
            run.setStatus(totals.failed > 0 ? MonthlyBillingRunStatus.PARTIAL : MonthlyBillingRunStatus.SUCCESS);
            run.setErrorMessage(null);
        }
        runRepository.save(run);
    }

    private LocalDate enabledFrom() {
        return controlRepository.findById(CONTROL_ID)
                .map(MonthlyBillingControlEntity::getEnabledFromMonth)
                .orElseGet(() -> LocalDate.now(INDIA).withDayOfMonth(1));
    }

    static ZoneId zoneOf(SpaceEntity space) {
        String raw = space != null ? space.getTimezone() : null;
        if (raw == null || raw.isBlank()) {
            return INDIA;
        }
        try {
            return ZoneId.of(raw.trim());
        } catch (DateTimeException ex) {
            return INDIA;
        }
    }

    private static boolean isAccommodation(SpaceType type) {
        return type == SpaceType.PG
                || type == SpaceType.HOSTEL
                || type == SpaceType.CO_LIVING
                || type == SpaceType.RENTAL
                || type == SpaceType.CORPORATE_STAY;
    }

    private static void bump(Map<String, Integer> skips, String reason) {
        skips.merge(reason, 1, Integer::sum);
    }

    private static String summarize(Map<String, Integer> skips) {
        if (skips.isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        skips.forEach((reason, count) -> {
            if (!text.isEmpty()) {
                text.append("; ");
            }
            text.append(reason).append('=').append(count);
        });
        return text.toString();
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private static String trimSummary(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 4000 ? value : value.substring(0, 4000);
    }

    private record Totals(
            int eligibleAccommodation,
            int eligibleMess,
            int created,
            int skipped,
            int failed,
            int queued,
            String skipSummary) {}
}
