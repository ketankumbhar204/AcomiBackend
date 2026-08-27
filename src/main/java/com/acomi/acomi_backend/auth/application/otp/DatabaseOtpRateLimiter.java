package com.acomi.acomi_backend.auth.application.otp;

import com.acomi.acomi_backend.auth.domain.model.OtpPurpose;
import com.acomi.acomi_backend.auth.infrastructure.persistence.entity.AuthOtpEntity;
import com.acomi.acomi_backend.auth.infrastructure.persistence.repository.AuthOtpRepository;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.RateLimitedException;
import com.acomi.acomi_backend.config.security.OtpProperties;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class DatabaseOtpRateLimiter implements OtpRateLimiter {

    public static final String RESEND_COOLDOWN_MESSAGE = "Please wait before requesting another OTP.";
    public static final String RATE_LIMIT_MESSAGE = "Too many OTP requests. Please try again later.";

    private final AuthOtpRepository authOtpRepository;
    private final OtpProperties otpProperties;

    @Override
    public void assertSendAllowed(String mobileNumber, OtpPurpose purpose, String requestIp) {
        LocalDateTime now = LocalDateTime.now();

        authOtpRepository.findFirstByMobileNumberAndPurposeOrderByCreatedAtDesc(mobileNumber, purpose)
                .map(AuthOtpEntity::getCreatedAt)
                .map(createdAt -> createdAt.plusSeconds(otpProperties.getResendCooldownSeconds()))
                .filter(readyAt -> readyAt.isAfter(now))
                .ifPresent(readyAt -> {
                    throw new RateLimitedException(RESEND_COOLDOWN_MESSAGE, secondsUntil(now, readyAt));
                });

        LocalDateTime mobileWindowStart = now.minusSeconds(otpProperties.getSendWindowSeconds());
        long mobileCount = authOtpRepository.countByMobileNumberAndPurposeAndCreatedAtAfter(
                mobileNumber, purpose, mobileWindowStart);
        if (mobileCount >= otpProperties.getMaxSendsPerWindow()) {
            throw new BusinessException(RATE_LIMIT_MESSAGE, HttpStatus.TOO_MANY_REQUESTS);
        }

        if (!StringUtils.hasText(requestIp)) {
            return;
        }
        LocalDateTime ipWindowStart = now.minusSeconds(otpProperties.getIpSendWindowSeconds());
        long ipCount = authOtpRepository.countByRequestIpAndCreatedAtAfter(requestIp, ipWindowStart);
        if (ipCount >= otpProperties.getIpMaxSendsPerWindow()) {
            throw new BusinessException(RATE_LIMIT_MESSAGE, HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    /** Rounds up so a caller that waits the reported seconds is never one tick early. */
    private static long secondsUntil(LocalDateTime now, LocalDateTime readyAt) {
        long millis = Duration.between(now, readyAt).toMillis();
        return Math.max(1, (long) Math.ceil(millis / 1000.0));
    }
}
