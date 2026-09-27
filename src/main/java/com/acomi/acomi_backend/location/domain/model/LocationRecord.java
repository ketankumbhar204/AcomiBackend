package com.acomi.acomi_backend.location.domain.model;

/**
 * Application-facing location row. {@code officeName} is the original source value
 * and is never mutated. {@code location} is the display/search form.
 */
public record LocationRecord(
        String officeName,
        String location,
        String cityTaluka,
        String district,
        String state,
        String pincode) {}
