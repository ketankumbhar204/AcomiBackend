package com.acomi.acomi_backend.auth.application.otp;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.auth.domain.model.OtpPurpose;
import com.acomi.acomi_backend.auth.infrastructure.persistence.entity.AuthOtpEntity;
import com.acomi.acomi_backend.auth.infrastructure.persistence.repository.AuthOtpRepository;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.RateLimitedException;
import com.acomi.acomi_backend.config.security.OtpProperties;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class DatabaseOtpRateLimiterTest {

    @Mock
    private AuthOtpRepository authOtpRepository;

    private DatabaseOtpRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        OtpProperties properties = new OtpProperties();
        properties.setResendCooldownSeconds(60);
        properties.setMaxSendsPerWindow(5);
        properties.setSendWindowSeconds(900);
        properties.setIpMaxSendsPerWindow(2);
        properties.setIpSendWindowSeconds(900);
        rateLimiter = new DatabaseOtpRateLimiter(authOtpRepository, properties);
    }

    @Test
    void ipLimit_isEnforced() {
        when(authOtpRepository.findFirstByMobileNumberAndPurposeOrderByCreatedAtDesc(
                        "9876543210", OtpPurpose.REGISTER))
                .thenReturn(Optional.empty());
        when(authOtpRepository.countByMobileNumberAndPurposeAndCreatedAtAfter(
                        org.mockito.ArgumentMatchers.eq("9876543210"),
                        org.mockito.ArgumentMatchers.eq(OtpPurpose.REGISTER),
                        org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(0L);
        when(authOtpRepository.countByRequestIpAndCreatedAtAfter(
                        org.mockito.ArgumentMatchers.eq("10.0.0.8"),
                        org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(2L);

        assertThatThrownBy(() ->
                        rateLimiter.assertSendAllowed("9876543210", OtpPurpose.REGISTER, "10.0.0.8"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DatabaseOtpRateLimiter.RATE_LIMIT_MESSAGE)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void cooldown_isEnforcedPerMobileAndPurpose() {
        AuthOtpEntity latest = AuthOtpEntity.builder()
                .mobileNumber("9876543210")
                .purpose(OtpPurpose.REGISTER)
                .codeHash("hash")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .maxAttempts(5)
                .build();
        latest.setCreatedAt(LocalDateTime.now().minusSeconds(10));
        when(authOtpRepository.findFirstByMobileNumberAndPurposeOrderByCreatedAtDesc(
                        "9876543210", OtpPurpose.REGISTER))
                .thenReturn(Optional.of(latest));

        assertThatThrownBy(() ->
                        rateLimiter.assertSendAllowed("9876543210", OtpPurpose.REGISTER, "10.0.0.8"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DatabaseOtpRateLimiter.RESEND_COOLDOWN_MESSAGE);
    }

    @Test
    void cooldown_isEnforcedForChangeMobile() {
        AuthOtpEntity latest = AuthOtpEntity.builder()
                .mobileNumber("9123456789")
                .purpose(OtpPurpose.CHANGE_MOBILE)
                .codeHash("hash")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .maxAttempts(5)
                .build();
        latest.setCreatedAt(LocalDateTime.now().minusSeconds(10));
        when(authOtpRepository.findFirstByMobileNumberAndPurposeOrderByCreatedAtDesc(
                        "9123456789", OtpPurpose.CHANGE_MOBILE))
                .thenReturn(Optional.of(latest));

        assertThatThrownBy(() ->
                        rateLimiter.assertSendAllowed("9123456789", OtpPurpose.CHANGE_MOBILE, "10.0.0.8"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DatabaseOtpRateLimiter.RESEND_COOLDOWN_MESSAGE);
    }

    @Test
    void cooldown_reportsRemainingSecondsSoClientsCanCountDown() {
        AuthOtpEntity latest = AuthOtpEntity.builder()
                .mobileNumber("9876543210")
                .purpose(OtpPurpose.LOGIN)
                .codeHash("hash")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .maxAttempts(5)
                .build();
        latest.setCreatedAt(LocalDateTime.now().minusSeconds(15));
        when(authOtpRepository.findFirstByMobileNumberAndPurposeOrderByCreatedAtDesc(
                        "9876543210", OtpPurpose.LOGIN))
                .thenReturn(Optional.of(latest));

        assertThatThrownBy(() ->
                        rateLimiter.assertSendAllowed("9876543210", OtpPurpose.LOGIN, "10.0.0.8"))
                .isInstanceOf(RateLimitedException.class)
                .extracting(ex -> ((RateLimitedException) ex).getRetryAfterSeconds())
                .isEqualTo(45L);
    }
}
