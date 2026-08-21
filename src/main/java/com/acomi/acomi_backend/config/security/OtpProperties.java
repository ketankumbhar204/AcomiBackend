package com.acomi.acomi_backend.config.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "acomi.otp")
public class OtpProperties {

    /** Numeric OTP length. Default 6. */
    private int length = 6;

    /** OTP lifetime in seconds. Default 5 minutes. */
    private int ttlSeconds = 300;

    /** Maximum verification attempts per OTP. */
    private int maxAttempts = 5;

    /** Minimum seconds between OTP sends for the same mobile and purpose. */
    private int resendCooldownSeconds = 60;

    /** Maximum OTP sends per mobile and purpose inside the send window. */
    private int maxSendsPerWindow = 5;

    /** Window in seconds used with {@link #maxSendsPerWindow}. */
    private int sendWindowSeconds = 900;

    /** Maximum OTP sends per IP inside the IP send window. */
    private int ipMaxSendsPerWindow = 20;

    /** Window in seconds used with {@link #ipMaxSendsPerWindow}. */
    private int ipSendWindowSeconds = 900;

    /** Registration verification token lifetime in seconds. Default 10 minutes. */
    private int verificationTokenTtlSeconds = 600;

    /**
     * Delivery implementation: {@code none} (default) or {@code dev}.
     * Production must never use {@code dev}.
     */
    private String sender = "none";

    /** HMAC secret used to hash OTPs and verification tokens. Not the JWT secret. */
    private String hashSecret;
}
