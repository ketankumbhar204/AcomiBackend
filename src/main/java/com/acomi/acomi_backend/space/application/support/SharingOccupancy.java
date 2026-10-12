package com.acomi.acomi_backend.space.application.support;

/**
 * Sharing buckets derived from the free-text Excel sharing column.
 * Blank text matches nothing. A combined value such as {@code 2/3 Sharing} matches both buckets.
 */
public enum SharingOccupancy {
    SINGLE,
    TWO,
    THREE,
    FOUR,
    FIVE,
    SIX_PLUS
}
