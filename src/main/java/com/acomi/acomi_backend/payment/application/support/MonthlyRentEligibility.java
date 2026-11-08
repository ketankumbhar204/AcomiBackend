package com.acomi.acomi_backend.payment.application.support;

import com.acomi.acomi_backend.dashboard.application.support.OccupancyBillingCalculator;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.domain.model.StayKind;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.payment.application.service.SpacePaymentGenerationService;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Who receives an automatic calendar-month rent row.
 *
 * <p>OPEN_ENDED daily-rate stays are always excluded here. Their payable rent stays on
 * the month-close job and vacate settlement. SHORT stays stay on the short-stay charge.
 */
public final class MonthlyRentEligibility {

    public static final String GENERATE = "GENERATE";
    public static final String SHORT_STAY = "SHORT_STAY";
    public static final String OPEN_ENDED_MONTH_CLOSE = "OPEN_ENDED_MONTH_CLOSE";
    public static final String RESERVED = "RESERVED";
    public static final String NOT_MOVED_IN = "NOT_MOVED_IN";
    public static final String VACATED_BEFORE_PERIOD = "VACATED_BEFORE_PERIOD";
    public static final String NOT_STARTED = "NOT_STARTED";
    public static final String INACTIVE_MEMBER = "INACTIVE_MEMBER";
    public static final String NOT_BILLABLE = "NOT_BILLABLE";
    public static final String RESERVATION_ADVANCE_COVERS_PERIOD = "RESERVATION_ADVANCE_COVERS_PERIOD";
    public static final String INVALID = "INVALID";

    private MonthlyRentEligibility() {}

    public record Evaluation(boolean generate, String reason) {

        public static Evaluation allow() {
            return new Evaluation(true, GENERATE);
        }

        public static Evaluation skip(String reason) {
            return new Evaluation(false, reason);
        }
    }

    /**
     * Eligibility as of the occupancy's stored move-in and vacate dates, not merely
     * "the member record is active today". {@code asOf} is the billing date used by
     * the shared payable-rent gate for non-open-ended stays.
     */
    public static Evaluation evaluate(OccupancyEntity occupancy, YearMonth month, LocalDate asOf) {
        if (occupancy == null || month == null || asOf == null) {
            return Evaluation.skip(INVALID);
        }
        if (occupancy.getStayKind() == StayKind.SHORT) {
            return Evaluation.skip(SHORT_STAY);
        }
        if (OccupancyBillingCalculator.isOpenEndedDailyRate(occupancy)) {
            return Evaluation.skip(OPEN_ENDED_MONTH_CLOSE);
        }
        if (occupancy.getStatus() == OccupancyStatus.RESERVED) {
            return Evaluation.skip(RESERVED);
        }
        if (occupancy.getReservedAt() != null && occupancy.getActualMoveInAt() == null) {
            return Evaluation.skip(NOT_MOVED_IN);
        }
        MemberEntity member = occupancy.getMember();
        if (member != null && !member.isActive()) {
            return Evaluation.skip(INACTIVE_MEMBER);
        }
        LocalDate end = OccupancyBillingCalculator.resolveOccupancyEndDate(occupancy);
        if (end != null && end.isBefore(month.atDay(1))) {
            return Evaluation.skip(VACATED_BEFORE_PERIOD);
        }
        LocalDate start = OccupancyBillingCalculator.resolveOccupancyStartDate(occupancy);
        if (start != null && start.isAfter(month.atEndOfMonth())) {
            return Evaluation.skip(NOT_STARTED);
        }
        if (!OccupancyBillingCalculator.shouldGeneratePayableRent(occupancy, month, asOf)) {
            return Evaluation.skip(NOT_BILLABLE);
        }
        if (SpacePaymentGenerationService.isReservationFirstPeriodRentFrozen(occupancy, month)) {
            return Evaluation.skip(RESERVATION_ADVANCE_COVERS_PERIOD);
        }
        return Evaluation.allow();
    }
}
