package com.acomi.acomi_backend.auth.application.service;

import com.acomi.acomi_backend.auth.application.otp.OtpDispatchResult;
import com.acomi.acomi_backend.auth.application.otp.OtpGenerator;
import com.acomi.acomi_backend.auth.application.otp.OtpHashService;
import com.acomi.acomi_backend.auth.application.otp.OtpRateLimiter;
import com.acomi.acomi_backend.auth.application.otp.OtpSender;
import com.acomi.acomi_backend.auth.application.otp.RegistrationVerification;
import com.acomi.acomi_backend.auth.application.otp.TwoFactorOtpClient;
import com.acomi.acomi_backend.auth.domain.model.OtpPurpose;
import com.acomi.acomi_backend.auth.infrastructure.persistence.entity.AuthOtpEntity;
import com.acomi.acomi_backend.auth.infrastructure.persistence.repository.AuthOtpRepository;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.config.security.OtpProperties;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OtpService {

    static final String INVALID_OTP_MESSAGE = "Invalid OTP";
    static final String EXPIRED_OTP_MESSAGE = "OTP has expired. Request a new one.";
    static final String CONSUMED_OTP_MESSAGE = "OTP is no longer valid. Request a new one.";
    static final String MAX_ATTEMPTS_MESSAGE = "Too many incorrect attempts. Request a new OTP.";
    static final String INVALID_VERIFICATION_TOKEN_MESSAGE = "Invalid or expired verification token.";
    static final String EXPIRED_VERIFICATION_TOKEN_MESSAGE = "Verification token has expired. Request a new OTP.";
    static final String CONSUMED_VERIFICATION_TOKEN_MESSAGE =
            "This verification token has already been used.";
    static final String UNSUPPORTED_PURPOSE_MESSAGE = "OTP purpose is not supported.";

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final int VERIFICATION_TOKEN_BYTES = 32;

    private final AuthOtpRepository authOtpRepository;
    private final OtpProperties otpProperties;
    private final OtpSender otpSender;
    private final OtpGenerator otpGenerator;
    private final OtpHashService otpHashService;
    private final OtpRateLimiter otpRateLimiter;
    private final Optional<TwoFactorOtpClient> twoFactorOtpClient;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public OtpDispatchResult sendOtp(String mobileNumber, OtpPurpose purpose, String requestIp) {
        return sendOtp(mobileNumber, purpose, requestIp, true);
    }

    @Transactional
    public OtpDispatchResult sendOtp(
            String mobileNumber, OtpPurpose purpose, String requestIp, boolean dispatchToProvider) {
        requireSupportedPurpose(purpose);
        otpRateLimiter.assertSendAllowed(mobileNumber, purpose, requestIp);

        LocalDateTime now = LocalDateTime.now();
        authOtpRepository.consumeUnusedOtps(now, mobileNumber, purpose);
        authOtpRepository.consumeUnusedVerificationTokens(now, mobileNumber, purpose);

        boolean external = usesExternalProvider();
        String otpMaterial = external ? UUID.randomUUID().toString() : otpGenerator.generate();
        AuthOtpEntity entity = AuthOtpEntity.builder()
                .mobileNumber(mobileNumber)
                .purpose(purpose)
                .codeHash(otpHashService.hashOtp(mobileNumber, purpose, otpMaterial))
                .expiresAt(now.plusSeconds(otpProperties.getTtlSeconds()))
                .attemptCount(0)
                .maxAttempts(otpProperties.getMaxAttempts())
                .requestIp(requestIp)
                .build();
        authOtpRepository.save(entity);

        try {
            if (dispatchToProvider) {
                if (external) {
                    twoFactorOtpClient.orElseThrow().sendOtp(mobileNumber);
                } else {
                    otpSender.send(mobileNumber, otpMaterial, purpose);
                }
            }
        } catch (RuntimeException ex) {
            entity.setConsumedAt(LocalDateTime.now());
            authOtpRepository.save(entity);
            throw ex;
        }
        log.info("OTP dispatch requested");

        return new OtpDispatchResult(
                otpProperties.getTtlSeconds(), otpProperties.getResendCooldownSeconds());
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public RegistrationVerification verifyRegistrationOtp(String mobileNumber, String otp) {
        return verifyAndIssueToken(mobileNumber, otp, OtpPurpose.REGISTER);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public RegistrationVerification verifyAndIssueToken(String mobileNumber, String otp, OtpPurpose purpose) {
        AuthOtpEntity entity = verifyAndConsumeInternal(mobileNumber, otp, purpose);
        LocalDateTime now = LocalDateTime.now();
        String rawToken = generateVerificationToken();
        entity.setVerificationTokenHash(
                otpHashService.hashVerificationToken(mobileNumber, purpose, rawToken));
        entity.setVerificationTokenExpiresAt(now.plusSeconds(otpProperties.getVerificationTokenTtlSeconds()));
        entity.setVerificationTokenConsumedAt(null);
        authOtpRepository.save(entity);
        return new RegistrationVerification(rawToken, otpProperties.getVerificationTokenTtlSeconds());
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void verifyAndConsume(String mobileNumber, String otp, OtpPurpose purpose) {
        verifyAndConsumeInternal(mobileNumber, otp, purpose);
    }

    @Transactional
    public void consumeRegistrationVerificationToken(String mobileNumber, String rawToken) {
        consumeVerificationToken(mobileNumber, rawToken, OtpPurpose.REGISTER);
    }

    @Transactional
    public void consumeVerificationToken(String mobileNumber, String rawToken, OtpPurpose purpose) {
        requireSupportedPurpose(purpose);
        if (!StringUtils.hasText(rawToken)) {
            throw new BusinessException(INVALID_VERIFICATION_TOKEN_MESSAGE);
        }

        String hash = otpHashService.hashVerificationToken(mobileNumber, purpose, rawToken);
        AuthOtpEntity entity = authOtpRepository.findByVerificationTokenHashForUpdate(hash)
                .orElseThrow(() -> new BusinessException(INVALID_VERIFICATION_TOKEN_MESSAGE));

        if (entity.getPurpose() != purpose
                || !mobileNumber.equals(entity.getMobileNumber())
                || entity.getConsumedAt() == null
                || !StringUtils.hasText(entity.getVerificationTokenHash())) {
            throw new BusinessException(INVALID_VERIFICATION_TOKEN_MESSAGE);
        }
        if (entity.getVerificationTokenConsumedAt() != null) {
            throw new BusinessException(CONSUMED_VERIFICATION_TOKEN_MESSAGE);
        }
        if (entity.getVerificationTokenExpiresAt() == null
                || !entity.getVerificationTokenExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(EXPIRED_VERIFICATION_TOKEN_MESSAGE);
        }

        entity.setVerificationTokenConsumedAt(LocalDateTime.now());
        authOtpRepository.save(entity);
    }

    private AuthOtpEntity verifyAndConsumeInternal(String mobileNumber, String otp, OtpPurpose purpose) {
        requireSupportedPurpose(purpose);
        if (!StringUtils.hasText(otp)) {
            throw new BusinessException(INVALID_OTP_MESSAGE);
        }

        List<AuthOtpEntity> latest = authOtpRepository.findLatestForUpdate(
                mobileNumber, purpose, PageRequest.of(0, 1));
        if (latest.isEmpty()) {
            throw new BusinessException(INVALID_OTP_MESSAGE);
        }

        AuthOtpEntity entity = latest.get(0);
        LocalDateTime now = LocalDateTime.now();

        if (entity.getConsumedAt() != null) {
            throw new BusinessException(CONSUMED_OTP_MESSAGE);
        }
        if (entity.getExpiresAt() == null || !entity.getExpiresAt().isAfter(now)) {
            throw new BusinessException(EXPIRED_OTP_MESSAGE);
        }
        if (entity.getAttemptCount() >= entity.getMaxAttempts()) {
            throw new BusinessException(MAX_ATTEMPTS_MESSAGE);
        }

        if (usesExternalProvider()) {
            try {
                twoFactorOtpClient.orElseThrow().verifyOtp(mobileNumber, otp);
            } catch (BusinessException ex) {
                entity.setAttemptCount(entity.getAttemptCount() + 1);
                authOtpRepository.saveAndFlush(entity);
                if (entity.getAttemptCount() >= entity.getMaxAttempts()) {
                    throw new BusinessException(MAX_ATTEMPTS_MESSAGE);
                }
                throw ex;
            }
        } else {
            String presentedHash = otpHashService.hashOtp(mobileNumber, purpose, otp);
            if (!otpHashService.matches(entity.getCodeHash(), presentedHash)) {
                entity.setAttemptCount(entity.getAttemptCount() + 1);
                authOtpRepository.saveAndFlush(entity);
                if (entity.getAttemptCount() >= entity.getMaxAttempts()) {
                    throw new BusinessException(MAX_ATTEMPTS_MESSAGE);
                }
                throw new BusinessException(INVALID_OTP_MESSAGE);
            }
        }

        entity.setConsumedAt(now);
        authOtpRepository.save(entity);
        log.info("OTP verified successfully");
        return entity;
    }

    private void requireSupportedPurpose(OtpPurpose purpose) {
        if (purpose == null) {
            throw new BusinessException("OTP purpose is required");
        }
        if (purpose != OtpPurpose.REGISTER
                && purpose != OtpPurpose.LOGIN
                && purpose != OtpPurpose.RESET_PASSWORD
                && purpose != OtpPurpose.ACCOUNT_DELETION) {
            throw new BusinessException(UNSUPPORTED_PURPOSE_MESSAGE);
        }
    }

    private boolean usesExternalProvider() {
        return twoFactorOtpClient != null && twoFactorOtpClient.isPresent();
    }

    private String generateVerificationToken() {
        byte[] bytes = new byte[VERIFICATION_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
