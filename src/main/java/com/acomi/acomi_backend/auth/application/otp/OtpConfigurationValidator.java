package com.acomi.acomi_backend.auth.application.otp;

import com.acomi.acomi_backend.config.security.OtpProperties;
import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class OtpConfigurationValidator {

    private static final int MIN_HASH_SECRET_LENGTH = 32;

    private final OtpProperties otpProperties;
    private final Environment environment;

    @PostConstruct
    void validate() {
        if (!StringUtils.hasText(otpProperties.getHashSecret())
                || otpProperties.getHashSecret().length() < MIN_HASH_SECRET_LENGTH) {
            throw new IllegalStateException("acomi.otp.hash-secret must be at least 32 characters");
        }
        if (otpProperties.getLength() < 4 || otpProperties.getLength() > 8) {
            throw new IllegalStateException("acomi.otp.length must be between 4 and 8");
        }
        String sender = otpProperties.getSender() == null
                ? "none"
                : otpProperties.getSender().trim().toLowerCase(Locale.ROOT);
        boolean production = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile));
        if (production && "dev".equals(sender)) {
            throw new IllegalStateException("Development OTP sender cannot be enabled in production");
        }
        if ("twofactor".equals(sender)
                && (otpProperties.getTwoFactor() == null
                        || !StringUtils.hasText(otpProperties.getTwoFactor().getApiKey()))) {
            throw new IllegalStateException(
                    "acomi.otp.twofactor.api-key is required when acomi.otp.sender is twofactor");
        }
    }
}
