package com.acomi.acomi_backend.payment.application.service;

import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import com.acomi.acomi_backend.payment.application.service.SpacePaymentGenerationService.MonthlyRentEnsureResult;
import com.acomi.acomi_backend.payment.application.service.SpacePaymentGenerationService.MonthlyRentWrite;
import com.acomi.acomi_backend.payment.application.support.MonthlyRentEligibility;
import com.acomi.acomi_backend.payment.application.support.MonthlyRentEligibility.Evaluation;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * One occupant, one transaction. A unique-key collision rolls back only this
 * occupant. The caller treats that as an already-present obligation.
 */
@Service
@RequiredArgsConstructor
public class MonthlyRentMemberBilling {

    private final OccupancyRepository occupancyRepository;
    private final SpacePaymentGenerationService paymentGenerationService;
    private final MonthlyBillingNotificationService notificationService;

    public record Result(String outcome, boolean notificationQueued) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result bill(UUID occupancyId, YearMonth month, LocalDate asOf) {
        OccupancyEntity occupancy = occupancyRepository.findById(occupancyId).orElse(null);
        if (occupancy == null || occupancy.getSpace() == null) {
            return new Result(MonthlyRentEligibility.NOT_BILLABLE, false);
        }
        Evaluation evaluation = MonthlyRentEligibility.evaluate(occupancy, month, asOf);
        if (!evaluation.generate()) {
            return new Result(evaluation.reason(), false);
        }
        MonthlyRentEnsureResult ensured =
                paymentGenerationService.ensureAutomaticMonthlyRent(occupancy.getSpace(), occupancy, month);
        if (ensured.write() != MonthlyRentWrite.CREATED) {
            return new Result(outcome(ensured.write()), false);
        }
        boolean queued = notificationService.enqueue(ensured.payment(), month);
        return new Result("CREATED", queued);
    }

    private static String outcome(MonthlyRentWrite write) {
        return switch (write) {
            case FROZEN -> "CANCELLED_OR_REFUNDED";
            case NOT_CHARGEABLE -> MonthlyRentEligibility.NOT_BILLABLE;
            case ALREADY_PRESENT -> "ALREADY_PRESENT";
            case CREATED -> "CREATED";
        };
    }
}
