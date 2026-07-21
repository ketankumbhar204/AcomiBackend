package com.countin.countin_backend.meal.domain.model;

public enum DailyMenuStatus {
    DRAFT,
    /** Live menu matches what customers can access. */
    PUBLISHED,
    /**
     * Owner edited after share. Working copy differs from {@code published_snapshot};
     * customers keep seeing the last shared snapshot until republish.
     */
    MODIFIED
}
