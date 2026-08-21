package com.acomi.acomi_backend.auth.domain.model;

/**
 * Strongly typed OTP purpose. Additional values such as PASSWORD_RESET or OTP_LOGIN
 * can be added later without a second OTP implementation.
 */
public enum OtpPurpose {
    REGISTER,
    ACCOUNT_DELETION
}
