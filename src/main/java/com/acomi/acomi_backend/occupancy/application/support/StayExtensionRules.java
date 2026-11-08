package com.acomi.acomi_backend.occupancy.application.support;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.occupancy.domain.model.StayKind;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * Planned-end and short-stay period rules for an extension.
 * A short stay from 1 November through 8 November is the half-open interval
 * [1 November, 8 November), which is seven nights. Monthly billing stays inclusive.
 */
public final class StayExtensionRules {

    public static final String ACTIVE_ONLY = "Only an active stay can be extended.";
    public static final String AFTER_START = "The new planned end must be after the stay start.";
    public static final String AFTER_CURRENT = "The new planned end must be after the current planned end.";
    public static final String AFTER_BUSINESS_DATE = "The new planned end must be after the space business date.";
    public static final String ON_OR_AFTER_BUSINESS_DATE =
            "The new planned end must be on or after the space business date.";
    public static final String SHORT_NEEDS_CURRENT_END =
            "A short stay needs a planned end before it can be extended.";
    public static final String CROSSES_MONTH =
            "A short-stay extension cannot cover nights in more than one calendar month.";
    public static final String AMOUNT_NOT_FOR_MONTHLY =
            "Monthly and open-ended stays do not take an extension charge. Leave the additional amount empty.";
    public static final String AMOUNT_NEGATIVE = "The additional amount cannot be negative.";
    public static final String RENT_NOT_FOR_SHORT = "A short stay does not use a monthly rent change.";
    public static final String RENT_FIELDS = "A new monthly rent needs the month it starts.";
    public static final String RENT_MONTH_BEFORE_START =
            "The new rent cannot start before the stay's first month.";
    public static final String RENT_NEGATIVE = "The new monthly rent cannot be negative.";
    public static final String RENT_ONLY_OPEN_ENDED =
            "A rent-only revision without a new planned end is only for open-ended stays.";
    public static final String RENT_UNCHANGED = "Choose a new daily rent that differs from the current rate.";
    public static final String RENT_EFFECTIVE_BEFORE_BUSINESS =
            "The new daily rent cannot start before the space business date.";
    public static final String RENT_EFFECTIVE_BEFORE_START =
            "The new daily rent cannot start before the stay start date.";
    public static final String END_REQUIRED =
            "A new planned end date is required to extend this stay.";
    public static final String CURRENT_RENT_MISSING =
            "This stay has no current daily rent on record, so the rate cannot be revised.";

    private StayExtensionRules() {}

    public static int nights(LocalDate inclusiveStart, LocalDate exclusiveEnd) {
        return (int) ChronoUnit.DAYS.between(inclusiveStart, exclusiveEnd);
    }

    public static void validateNewEnd(
            LocalDate start, LocalDate currentEnd, LocalDate newEnd, LocalDate businessDate) {
        if (start == null || newEnd == null || businessDate == null || !newEnd.isAfter(start)) {
            throw new BusinessException("EXTENSION_DATE_INVALID", AFTER_START);
        }
        if (currentEnd == null) {
            if (!newEnd.isAfter(businessDate)) {
                throw new BusinessException("EXTENSION_DATE_INVALID", AFTER_BUSINESS_DATE);
            }
            return;
        }
        if (!newEnd.isAfter(currentEnd)) {
            throw new BusinessException("EXTENSION_DATE_INVALID", AFTER_CURRENT);
        }
        if (currentEnd.isBefore(businessDate) && newEnd.isBefore(businessDate)) {
            throw new BusinessException("EXTENSION_DATE_INVALID", ON_OR_AFTER_BUSINESS_DATE);
        }
    }

    public static boolean crossesCalendarMonth(LocalDate inclusiveStart, LocalDate exclusiveEnd) {
        if (inclusiveStart == null || exclusiveEnd == null || !exclusiveEnd.isAfter(inclusiveStart)) {
            return false;
        }
        LocalDate lastNight = exclusiveEnd.minusDays(1);
        return !YearMonth.from(inclusiveStart).equals(YearMonth.from(lastNight));
    }

    public static boolean isShortStay(StayKind stayKind) {
        return stayKind == StayKind.SHORT;
    }

    public static boolean hasCharge(BigDecimal additionalAmount) {
        return additionalAmount != null && additionalAmount.compareTo(BigDecimal.ZERO) > 0;
    }
}
