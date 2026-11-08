package com.acomi.acomi_backend.occupancy.domain.model;

/**
 * Values for {@code occupancies.pricing_basis}. Column is unconstrained VARCHAR for
 * backward compatibility; only these literals are written by the service.
 *
 * <ul>
 *   <li>{@link #FIXED_TOTAL} — Short Stay owner-entered agreed total
 *   <li>{@link #DAILY_RATE} — Until I Leave owner-entered daily rent ({@code rent_snapshot})
 *   <li>null — Monthly (or legacy OPEN_ENDED) monthly {@code rent_snapshot}
 * </ul>
 */
public final class OccupancyPricingBasis {

    public static final String FIXED_TOTAL = "FIXED_TOTAL";
    public static final String DAILY_RATE = "DAILY_RATE";

    private OccupancyPricingBasis() {}

    public static boolean isDailyRate(String pricingBasis) {
        return DAILY_RATE.equals(pricingBasis);
    }
}
