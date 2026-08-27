package com.acomi.acomi_backend.auth.domain.model;

/**
 * Strongly typed OTP purpose. Hashed into OTP and verification-token material so a token
 * issued for one purpose cannot be consumed for another.
 */
public enum OtpPurpose {
    REGISTER,
    LOGIN,
    RESET_PASSWORD,
    ACCOUNT_DELETION,
    CHANGE_MOBILE,
    /**
     * Public website property registration. Deliberately separate from REGISTER: a property
     * lead must not mint an account token, and an owner who already has an ACOMI account
     * must still be able to register a property.
     */
    PROPERTY_REGISTRATION,
    /**
     * Public website mess registration. Separate from PROPERTY_REGISTRATION so a
     * property verification token cannot submit a mess lead.
     */
    MESS_REGISTRATION
}
