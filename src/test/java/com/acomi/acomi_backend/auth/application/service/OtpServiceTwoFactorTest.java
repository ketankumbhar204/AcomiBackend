package com.acomi.acomi_backend.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.auth.application.otp.DatabaseOtpRateLimiter;
import com.acomi.acomi_backend.auth.application.otp.OtpGenerator;
import com.acomi.acomi_backend.auth.application.otp.OtpHashService;
import com.acomi.acomi_backend.auth.application.otp.OtpSender;
import com.acomi.acomi_backend.auth.application.otp.TwoFactorOtpClient;
import com.acomi.acomi_backend.auth.domain.model.OtpPurpose;
import com.acomi.acomi_backend.auth.infrastructure.persistence.entity.AuthOtpEntity;
import com.acomi.acomi_backend.auth.infrastructure.persistence.repository.AuthOtpRepository;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.config.security.OtpProperties;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtpServiceTwoFactorTest {

    private static final String MOBILE = "9876543210";

    @Mock
    private AuthOtpRepository authOtpRepository;

    @Mock
    private OtpSender otpSender;

    @Mock
    private TwoFactorOtpClient twoFactorOtpClient;

    private final List<AuthOtpEntity> store = new ArrayList<>();
    private OtpService otpService;

    @BeforeEach
    void setUp() {
        OtpProperties properties = new OtpProperties();
        properties.setLength(6);
        properties.setTtlSeconds(300);
        properties.setMaxAttempts(5);
        properties.setResendCooldownSeconds(60);
        properties.setMaxSendsPerWindow(5);
        properties.setSendWindowSeconds(900);
        properties.setIpMaxSendsPerWindow(20);
        properties.setIpSendWindowSeconds(900);
        properties.setVerificationTokenTtlSeconds(600);
        properties.setHashSecret("test-otp-hash-secret-must-be-32chars!");
        OtpHashService hashService = new OtpHashService(properties);
        when(authOtpRepository.save(any(AuthOtpEntity.class))).thenAnswer(invocation -> {
            AuthOtpEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(LocalDateTime.now());
            }
            store.removeIf(existing -> existing.getId().equals(entity.getId()));
            store.add(entity);
            return entity;
        });
        when(authOtpRepository.saveAndFlush(any(AuthOtpEntity.class))).thenAnswer(invocation ->
                authOtpRepository.save(invocation.getArgument(0)));
        when(authOtpRepository.findLatestForUpdate(any(), any(), any())).thenAnswer(invocation -> {
            String mobile = invocation.getArgument(0);
            OtpPurpose purpose = invocation.getArgument(1);
            return store.stream()
                    .filter(row -> row.getMobileNumber().equals(mobile) && row.getPurpose() == purpose)
                    .max(Comparator.comparing(AuthOtpEntity::getCreatedAt))
                    .map(List::of)
                    .orElseGet(List::of);
        });
        when(authOtpRepository.findFirstByMobileNumberAndPurposeOrderByCreatedAtDesc(any(), any()))
                .thenAnswer(invocation -> store.stream()
                        .filter(row -> row.getMobileNumber().equals(invocation.getArgument(0))
                                && row.getPurpose() == invocation.getArgument(1))
                        .max(Comparator.comparing(AuthOtpEntity::getCreatedAt)));
        otpService = new OtpService(
                authOtpRepository,
                properties,
                otpSender,
                new OtpGenerator(properties),
                hashService,
                new DatabaseOtpRateLimiter(authOtpRepository, properties),
                Optional.of(twoFactorOtpClient));
    }

    @Test
    void sendOtp_usesProviderAndDoesNotSendLocalCode() {
        otpService.sendOtp(MOBILE, OtpPurpose.REGISTER, "127.0.0.1");

        verify(twoFactorOtpClient).sendOtp(MOBILE);
        verify(otpSender, never()).send(any(), any(), any());
        assertThat(store).hasSize(1);
        assertThat(store.get(0).getConsumedAt()).isNull();
        assertThat(store.get(0).getCodeHash()).isNotBlank();
    }

    @Test
    void sendOtp_providerFailureDoesNotLeaveUsableOtp() {
        doThrow(new BusinessException("Unable to send OTP. Please try again later.", HttpStatus.SERVICE_UNAVAILABLE))
                .when(twoFactorOtpClient)
                .sendOtp(MOBILE);

        assertThatThrownBy(() -> otpService.sendOtp(MOBILE, OtpPurpose.REGISTER, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Unable to send OTP. Please try again later.");

        assertThat(store.get(0).getConsumedAt()).isNotNull();
    }

    @Test
    void verifyOtp_successConsumesProviderSession() {
        otpService.sendOtp(MOBILE, OtpPurpose.REGISTER, "127.0.0.1");

        var verification = otpService.verifyRegistrationOtp(MOBILE, "482731");

        verify(twoFactorOtpClient).verifyOtp(MOBILE, "482731");
        assertThat(verification.verificationToken()).isNotBlank();
        assertThat(store.get(0).getConsumedAt()).isNotNull();
    }

    @Test
    void verifyOtp_invalidDoesNotIssueToken() {
        otpService.sendOtp(MOBILE, OtpPurpose.REGISTER, "127.0.0.1");
        doThrow(new BusinessException("Invalid OTP"))
                .when(twoFactorOtpClient)
                .verifyOtp(MOBILE, "000000");

        assertThatThrownBy(() -> otpService.verifyRegistrationOtp(MOBILE, "000000"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid OTP");

        assertThat(store.get(0).getConsumedAt()).isNull();
        assertThat(store.get(0).getAttemptCount()).isEqualTo(1);
        assertThat(store.get(0).getVerificationTokenHash()).isNull();
    }

    @Test
    void verifyOtp_providerUnavailableDoesNotIssueToken() {
        otpService.sendOtp(MOBILE, OtpPurpose.REGISTER, "127.0.0.1");
        doThrow(new BusinessException(
                        "We couldn't verify the code right now. Please try again.", HttpStatus.SERVICE_UNAVAILABLE))
                .when(twoFactorOtpClient)
                .verifyOtp(MOBILE, "482731");

        assertThatThrownBy(() -> otpService.verifyRegistrationOtp(MOBILE, "482731"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("We couldn't verify the code right now. Please try again.");

        assertThat(store.get(0).getConsumedAt()).isNull();
        assertThat(store.get(0).getVerificationTokenHash()).isNull();
    }
}
