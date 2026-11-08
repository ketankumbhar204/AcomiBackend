package com.acomi.acomi_backend.occupancy.application.support;

import com.acomi.acomi_backend.common.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Creating a short stay. Owner enters a daily rent; stay rent is daily × nights under the
 * half-open {@code [checkIn, checkout)} convention. Legacy rows that only store a fixed
 * {@code agreedTotal} (no daily {@code rentSnapshot}) remain valid.
 *
 * <p>Every occupied night must stay inside one calendar month. A later cross-month total is
 * still an open decision and is not created here.
 *
 * <p>Stay type is chosen per occupancy. {@code spaces.short_stay_enabled} is retained for
 * backward compatibility only and does not gate SHORT create.
 */
public final class ShortStayRules {

    /** @deprecated No longer thrown; property-level Short Stay gate removed. */
    @Deprecated
    public static final String DISABLED = "Short Stay is not turned on for this space.";

    public static final String END_REQUIRED = "A short stay needs a planned checkout date.";
    public static final String SAME_DAY =
            "A short stay checkout must be after the check-in date. The same date is not a stay.";
    public static final String CROSSES_MONTH =
            "A short stay cannot cover nights in more than one calendar month.";
    public static final String AGREED_TOTAL_REQUIRED = "Agreed total is required for a short stay.";
    public static final String DAILY_RENT_REQUIRED = "Daily rent is required for a short stay.";
    public static final String AGREED_TOTAL_NOT_ALLOWED = "An agreed total is only for a short stay.";
    /** @deprecated Prefer {@link #DAILY_RENT_REQUIRED}; rent_snapshot is now the daily rate. */
    @Deprecated
    public static final String RENT_NOT_ALLOWED =
            "A short stay must not store its total as monthly rent.";

    private ShortStayRules() {}

    /**
     * Validates SHORT create rules (dates + daily rate or legacy fixed total). The {@code enabled}
     * argument is ignored; kept for call-site compatibility.
     */
    public static void validateCreate(
            boolean enabled,
            LocalDate checkIn,
            LocalDate checkout,
            BigDecimal agreedTotal,
            BigDecimal rentSnapshot) {
        resolveStayRent(checkIn, checkout, rentSnapshot, agreedTotal);
    }

    /** Preferred entry: property flag is not consulted. */
    public static void validateCreate(
            LocalDate checkIn,
            LocalDate checkout,
            BigDecimal agreedTotal,
            BigDecimal rentSnapshot) {
        resolveStayRent(checkIn, checkout, rentSnapshot, agreedTotal);
    }

    public static void validateCharge(
            LocalDate checkIn, LocalDate checkout, BigDecimal agreedTotal, BigDecimal rentSnapshot) {
        resolveStayRent(checkIn, checkout, rentSnapshot, agreedTotal);
    }

    /**
     * Resolves the stay rent charged for a short stay.
     *
     * <ul>
     *   <li>When {@code dailyRate} ({@code rentSnapshot}) is set — stay rent = daily × nights.
     *   <li>Otherwise legacy {@code agreedTotal} is required and returned unchanged.
     * </ul>
     */
    public static BigDecimal resolveStayRent(
            LocalDate checkIn,
            LocalDate checkout,
            BigDecimal dailyRate,
            BigDecimal legacyAgreedTotal) {
        validateDates(checkIn, checkout);
        if (dailyRate != null) {
            if (dailyRate.signum() <= 0) {
                throw new BusinessException("DAILY_RENT_REQUIRED", DAILY_RENT_REQUIRED);
            }
            int nights = StayExtensionRules.nights(checkIn, checkout);
            return dailyRate.multiply(BigDecimal.valueOf(nights));
        }
        if (legacyAgreedTotal == null || legacyAgreedTotal.signum() <= 0) {
            throw new BusinessException("AGREED_TOTAL_REQUIRED", AGREED_TOTAL_REQUIRED);
        }
        return legacyAgreedTotal;
    }

    private static void validateDates(LocalDate checkIn, LocalDate checkout) {
        if (checkIn == null || checkout == null) {
            throw new BusinessException("SHORT_STAY_END_REQUIRED", END_REQUIRED);
        }
        if (!checkout.isAfter(checkIn)) {
            throw new BusinessException("SHORT_STAY_SAME_DAY", SAME_DAY);
        }
        if (StayExtensionRules.crossesCalendarMonth(checkIn, checkout)) {
            throw new BusinessException("SHORT_STAY_CROSSES_MONTH", CROSSES_MONTH);
        }
    }
}
