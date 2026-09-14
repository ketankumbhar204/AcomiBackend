package com.acomi.acomi_backend.space.domain.model;

/**
 * How configured prices relate to tax when {@code taxEnabled} is true.
 * When tax is disabled, mode is ignored (treat as no tax).
 */
public enum PriceTaxMode {
    /** Configured price is pre-tax; tax is added. */
    EXCLUSIVE,
    /** Configured price is customer-facing final; tax is reverse-split. */
    INCLUSIVE
}
