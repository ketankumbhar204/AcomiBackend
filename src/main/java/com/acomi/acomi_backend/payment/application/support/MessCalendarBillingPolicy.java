package com.acomi.acomi_backend.payment.application.support;

/**
 * Mess and Tiffin do not have a calendar-month subscription obligation.
 *
 * <p>Prepaid plans are meal packs with a price, included meals, and {@code validityDays}.
 * Activation credits the meal balance and does not insert a monthly space payment.
 * Pay-per-meal charges follow meal days and the existing activity sync. Daily and
 * one-time orders stay on that path. Pause is a participation status, not a
 * subscription billing calendar.
 */
public final class MessCalendarBillingPolicy {

    public static final String SKIP_REASON = "MESS_NOT_CALENDAR_MONTH";

    public static final String EXPLANATION =
            "No calendar-month Mess/Tiffin subscription charge exists. "
                    + "Prepaid plans bill when a pack is purchased and expire by validity days. "
                    + "Pay-per-meal, daily, and one-time orders stay on the existing meal billing. "
                    + "Paused, ended, and expired balances are not given a new monthly payment.";

    private MessCalendarBillingPolicy() {}

    public static boolean generatesCalendarMonthPayment() {
        return false;
    }
}
