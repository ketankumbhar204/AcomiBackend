package com.acomi.acomi_backend.auth.domain.model;

/**
 * Strongly typed OTP purpose. Hashed into OTP and verification-token material so a token
 * issued for one purpose cannot be consumed for another.
 */
public enum OtpPurpose {
    REGISTER,
    LOGIN,
    RESET_PASSWORD,
    ACCOUNT_DELETION
}
