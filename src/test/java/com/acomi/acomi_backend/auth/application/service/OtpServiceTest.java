package com.acomi.acomi_backend.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.auth.application.otp.DatabaseOtpRateLimiter;
import com.acomi.acomi_backend.auth.application.otp.OtpDispatchResult;
import com.acomi.acomi_backend.auth.application.otp.OtpGenerator;
import com.acomi.acomi_backend.auth.application.otp.OtpHashService;
import com.acomi.acomi_backend.auth.application.otp.OtpSender;
import com.acomi.acomi_backend.auth.application.otp.RegistrationVerification;
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
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtpServiceTest {

    private static final String MOBILE = "9876543210";
    private static final String OTHER_MOBILE = "9123456789";

    @Mock
    private AuthOtpRepository authOtpRepository;

    private final List<AuthOtpEntity> store = new ArrayList<>();
    private final CapturingOtpSender sender = new CapturingOtpSender();
    private OtpService otpService;
    private OtpHashService hashService;

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
        hashService = new OtpHashService(properties);
        wireStore();
        otpService = new OtpService(
                authOtpRepository,
                properties,
                sender,
                new OtpGenerator(properties),
                hashService,
                new DatabaseOtpRateLimiter(authOtpRepository, properties),
                Optional.empty());
    }

    @Test
    void sendOtp_persistsHashNotPlaintext() {
        otpService.sendOtp(MOBILE, OtpPurpose.REGISTER, "127.0.0.1");

        assertThat(store).hasSize(1);
        AuthOtpEntity saved = store.get(0);
        assertThat(sender.lastOtp).matches("\\d{6}");
        assertThat(saved.getCodeHash()).isNotEqualTo(sender.lastOtp);
        assertThat(saved.getCodeHash()).doesNotContain(sender.lastOtp);
        assertThat(saved.getCodeHash()).isEqualTo(
                hashService.hashOtp(MOBILE, OtpPurpose.REGISTER, sender.lastOtp));
        assertThat(saved.getPurpose()).isEqualTo(OtpPurpose.REGISTER);
        assertThat(saved.getConsumedAt()).isNull();
    }

    @Test
    void sendOtp_firstRequestSucceeds() {
        OtpDispatchResult result = otpService.sendOtp(MOBILE, OtpPurpose.REGISTER, "127.0.0.1");

        assertThat(result.expiresInSeconds()).isEqualTo(300);
        assertThat(result.resendAfterSeconds()).isEqualTo(60);
        assertThat(sender.lastPurpose).isEqualTo(OtpPurpose.REGISTER);
        assertThat(sender.lastMobile).isEqualTo(MOBILE);
    }

    @Test
    void sendOtp_immediateResendIsRejected() {
        otpService.sendOtp(MOBILE, OtpPurpose.REGISTER, "127.0.0.1");

        assertThatThrownBy(() -> otpService.sendOtp(MOBILE, OtpPurpose.REGISTER, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(DatabaseOtpRateLimiter.RESEND_COOLDOWN_MESSAGE)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void sendOtp_resendAfterCooldownSucceeds() {
        otpService.sendOtp(MOBILE, OtpPurpose.REGISTER, "127.0.0.1");
        store.get(0).setCreatedAt(LocalDateTime.now().minusSeconds(61));

        OtpDispatchResult result = otpService.sendOtp(MOBILE, OtpPurpose.REGISTER, "127.0.0.1");

        assertThat(result.expiresInSeconds()).isEqualTo(300);
        assertThat(store).hasSize(2);
        assertThat(store.get(0).getConsumedAt()).isNotNull();
    }

    @Test
    void verify_validOtpSucceedsAndReturnsVerificationToken() {
        otpService.sendOtp(MOBILE, OtpPurpose.REGISTER, "127.0.0.1");

        RegistrationVerification verification =
                otpService.verifyRegistrationOtp(MOBILE, sender.lastOtp);

        assertThat(verification.verificationToken()).isNotBlank();
        assertThat(verification.expiresInSeconds()).isEqualTo(600);
        assertThat(store.get(0).getConsumedAt()).isNotNull();
        assertThat(store.get(0).getVerificationTokenHash()).isNotEqualTo(verification.verificationToken());
        assertThat(store.get(0).getVerificationTokenHash()).doesNotContain(verification.verificationToken());
    }

    @Test
    void verify_wrongOtpFails() {
        otpService.sendOtp(MOBILE, OtpPurpose.REGISTER, "127.0.0.1");

        assertThatThrownBy(() -> otpService.verifyRegistrationOtp(MOBILE, "000001"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(OtpService.INVALID_OTP_MESSAGE);
        assertThat(store.get(0).getAttemptCount()).isEqualTo(1);
        assertThat(store.get(0).getConsumedAt()).isNull();
    }

    @Test
    void verify_expiredOtpFails() {
        otpService.sendOtp(MOBILE, OtpPurpose.REGISTER, "127.0.0.1");
        store.get(0).setExpiresAt(LocalDateTime.now().minusSeconds(1));

        assertThatThrownBy(() -> otpService.verifyRegistrationOtp(MOBILE, sender.lastOtp))
                .isInstanceOf(BusinessException.class)
                .hasMessage(OtpService.EXPIRED_OTP_MESSAGE);
    }

    @Test
    void verify_consumedOtpFails() {
        otpService.sendOtp(MOBILE, OtpPurpose.REGISTER, "127.0.0.1");
        String otp = sender.lastOtp;
        otpService.verifyRegistrationOtp(MOBILE, otp);

        assertThatThrownBy(() -> otpService.verifyRegistrationOtp(MOBILE, otp))
                .isInstanceOf(BusinessException.class)
                .hasMessage(OtpService.CONSUMED_OTP_MESSAGE);
    }

    @Test
    void verify_maxAttemptsEnforced() {
        otpService.sendOtp(MOBILE, OtpPurpose.REGISTER, "127.0.0.1");
        String validOtp = sender.lastOtp;

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> otpService.verifyRegistrationOtp(MOBILE, "000001"))
                    .isInstanceOf(BusinessException.class);
        }

        assertThatThrownBy(() -> otpService.verifyRegistrationOtp(MOBILE, validOtp))
                .isInstanceOf(BusinessException.class)
                .hasMessage(OtpService.MAX_ATTEMPTS_MESSAGE);
    }

    @Test
    void verify_requiresCorrectPurpose() {
        otpService.sendOtp(MOBILE, OtpPurpose.REGISTER, "127.0.0.1");

        assertThatThrownBy(() -> otpService.verifyAndConsume(MOBILE, sender.lastOtp, OtpPurpose.ACCOUNT_DELETION))
                .isInstanceOf(BusinessException.class)
                .hasMessage(OtpService.INVALID_OTP_MESSAGE);
    }

    @Test
    void verify_requiresCorrectMobile() {
        otpService.sendOtp(MOBILE, OtpPurpose.REGISTER, "127.0.0.1");

        assertThatThrownBy(() -> otpService.verifyRegistrationOtp(OTHER_MOBILE, sender.lastOtp))
                .isInstanceOf(BusinessException.class)
                .hasMessage(OtpService.INVALID_OTP_MESSAGE);
    }

    @Test
    void verificationToken_cannotBeUsedBeforeOtpVerification() {
        otpService.sendOtp(MOBILE, OtpPurpose.REGISTER, "127.0.0.1");

        assertThatThrownBy(() -> otpService.consumeRegistrationVerificationToken(MOBILE, "not-issued"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(OtpService.INVALID_VERIFICATION_TOKEN_MESSAGE);
    }

    @Test
    void verificationToken_expires() {
        otpService.sendOtp(MOBILE, OtpPurpose.REGISTER, "127.0.0.1");
        RegistrationVerification verification =
                otpService.verifyRegistrationOtp(MOBILE, sender.lastOtp);
        store.get(0).setVerificationTokenExpiresAt(LocalDateTime.now().minusSeconds(1));

        assertThatThrownBy(() ->
                        otpService.consumeRegistrationVerificationToken(MOBILE, verification.verificationToken()))
                .isInstanceOf(BusinessException.class)
                .hasMessage(OtpService.EXPIRED_VERIFICATION_TOKEN_MESSAGE);
    }

    @Test
    void verificationToken_cannotBeReused() {
        otpService.sendOtp(MOBILE, OtpPurpose.REGISTER, "127.0.0.1");
        RegistrationVerification verification =
                otpService.verifyRegistrationOtp(MOBILE, sender.lastOtp);

        otpService.consumeRegistrationVerificationToken(MOBILE, verification.verificationToken());

        assertThatThrownBy(() ->
                        otpService.consumeRegistrationVerificationToken(MOBILE, verification.verificationToken()))
                .isInstanceOf(BusinessException.class)
                .hasMessage(OtpService.CONSUMED_VERIFICATION_TOKEN_MESSAGE);
    }

    @Test
    void verificationToken_cannotBeUsedForAnotherMobile() {
        otpService.sendOtp(MOBILE, OtpPurpose.REGISTER, "127.0.0.1");
        RegistrationVerification verification =
                otpService.verifyRegistrationOtp(MOBILE, sender.lastOtp);

        assertThatThrownBy(() ->
                        otpService.consumeRegistrationVerificationToken(OTHER_MOBILE, verification.verificationToken()))
                .isInstanceOf(BusinessException.class)
                .hasMessage(OtpService.INVALID_VERIFICATION_TOKEN_MESSAGE);
    }

    @Test
    void verificationToken_isBoundToPurpose() {
        otpService.sendOtp(MOBILE, OtpPurpose.REGISTER, "127.0.0.1");
        RegistrationVerification registerToken =
                otpService.verifyAndIssueToken(MOBILE, sender.lastOtp, OtpPurpose.REGISTER);

        assertThatThrownBy(() ->
                        otpService.consumeVerificationToken(
                                MOBILE, registerToken.verificationToken(), OtpPurpose.LOGIN))
                .isInstanceOf(BusinessException.class)
                .hasMessage(OtpService.INVALID_VERIFICATION_TOKEN_MESSAGE);

        otpService.sendOtp(MOBILE, OtpPurpose.RESET_PASSWORD, "127.0.0.1");
        RegistrationVerification resetToken =
                otpService.verifyAndIssueToken(MOBILE, sender.lastOtp, OtpPurpose.RESET_PASSWORD);

        assertThatThrownBy(() ->
                        otpService.consumeVerificationToken(
                                MOBILE, resetToken.verificationToken(), OtpPurpose.ACCOUNT_DELETION))
                .isInstanceOf(BusinessException.class)
                .hasMessage(OtpService.INVALID_VERIFICATION_TOKEN_MESSAGE);

        otpService.sendOtp(MOBILE, OtpPurpose.LOGIN, "127.0.0.1");
        RegistrationVerification loginToken =
                otpService.verifyAndIssueToken(MOBILE, sender.lastOtp, OtpPurpose.LOGIN);
        otpService.consumeVerificationToken(MOBILE, loginToken.verificationToken(), OtpPurpose.LOGIN);

        assertThatThrownBy(() ->
                        otpService.consumeVerificationToken(
                                MOBILE, loginToken.verificationToken(), OtpPurpose.LOGIN))
                .isInstanceOf(BusinessException.class)
                .hasMessage(OtpService.CONSUMED_VERIFICATION_TOKEN_MESSAGE);
    }

    @Test
    void sendOtp_skipDispatch_doesNotCallSender() {
        otpService.sendOtp(MOBILE, OtpPurpose.LOGIN, "127.0.0.1", false);
        assertThat(sender.lastOtp).isNull();
        assertThat(store).hasSize(1);
        assertThat(store.get(0).getPurpose()).isEqualTo(OtpPurpose.LOGIN);
    }

    @Test
    void accountDeletionOtp_doesNotIssueRegistrationToken() {
        otpService.sendOtp(MOBILE, OtpPurpose.ACCOUNT_DELETION, "127.0.0.1");
        otpService.verifyAndConsume(MOBILE, sender.lastOtp, OtpPurpose.ACCOUNT_DELETION);

        assertThat(store.get(0).getConsumedAt()).isNotNull();
        assertThat(store.get(0).getVerificationTokenHash()).isNull();
    }

    @Test
    void sendOtp_doesNotReturnOtpThroughDispatchResult() {
        OtpDispatchResult result = otpService.sendOtp(MOBILE, OtpPurpose.REGISTER, "127.0.0.1");
        assertThat(result.toString()).doesNotContain(sender.lastOtp);
    }

    private void wireStore() {
        when(authOtpRepository.save(any(AuthOtpEntity.class))).thenAnswer(saveEntity());
        when(authOtpRepository.findFirstByMobileNumberAndPurposeOrderByCreatedAtDesc(any(), any()))
                .thenAnswer(invocation -> latest(invocation.getArgument(0), invocation.getArgument(1)));
        when(authOtpRepository.findLatestForUpdate(any(), any(), any()))
                .thenAnswer(invocation -> latest(invocation.getArgument(0), invocation.getArgument(1))
                        .map(List::of)
                        .orElseGet(List::of));
        when(authOtpRepository.countByMobileNumberAndPurposeAndCreatedAtAfter(any(), any(), any()))
                .thenAnswer(invocation -> store.stream()
                        .filter(row -> row.getMobileNumber().equals(invocation.getArgument(0)))
                        .filter(row -> row.getPurpose() == invocation.getArgument(1))
                        .filter(row -> row.getCreatedAt() != null
                                && row.getCreatedAt().isAfter(invocation.getArgument(2)))
                        .count());
        when(authOtpRepository.countByRequestIpAndCreatedAtAfter(any(), any()))
                .thenAnswer(invocation -> store.stream()
                        .filter(row -> invocation.getArgument(0).equals(row.getRequestIp()))
                        .filter(row -> row.getCreatedAt() != null
                                && row.getCreatedAt().isAfter(invocation.getArgument(1)))
                        .count());
        when(authOtpRepository.findByVerificationTokenHashForUpdate(any()))
                .thenAnswer(invocation -> store.stream()
                        .filter(row -> invocation.getArgument(0).equals(row.getVerificationTokenHash()))
                        .findFirst());
        when(authOtpRepository.consumeUnusedOtps(any(), any(), any())).thenAnswer(invocation -> {
            LocalDateTime now = invocation.getArgument(0);
            String mobile = invocation.getArgument(1);
            OtpPurpose purpose = invocation.getArgument(2);
            int count = 0;
            for (AuthOtpEntity row : store) {
                if (row.getMobileNumber().equals(mobile)
                        && row.getPurpose() == purpose
                        && row.getConsumedAt() == null) {
                    row.setConsumedAt(now);
                    count++;
                }
            }
            return count;
        });
        when(authOtpRepository.consumeUnusedVerificationTokens(any(), any(), any())).thenAnswer(invocation -> {
            LocalDateTime now = invocation.getArgument(0);
            String mobile = invocation.getArgument(1);
            OtpPurpose purpose = invocation.getArgument(2);
            int count = 0;
            for (AuthOtpEntity row : store) {
                if (row.getMobileNumber().equals(mobile)
                        && row.getPurpose() == purpose
                        && row.getVerificationTokenHash() != null
                        && row.getVerificationTokenConsumedAt() == null) {
                    row.setVerificationTokenConsumedAt(now);
                    count++;
                }
            }
            return count;
        });
    }

    private Optional<AuthOtpEntity> latest(String mobile, OtpPurpose purpose) {
        return store.stream()
                .filter(row -> row.getMobileNumber().equals(mobile) && row.getPurpose() == purpose)
                .max(Comparator.comparing(AuthOtpEntity::getCreatedAt));
    }

    private Answer<AuthOtpEntity> saveEntity() {
        return invocation -> {
            AuthOtpEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(LocalDateTime.now());
            }
            entity.setUpdatedAt(LocalDateTime.now());
            store.removeIf(existing -> existing.getId().equals(entity.getId()));
            store.add(entity);
            return entity;
        };
    }

    private static final class CapturingOtpSender implements OtpSender {
        private String lastMobile;
        private String lastOtp;
        private OtpPurpose lastPurpose;

        @Override
        public void send(String mobileNumber, String otp, OtpPurpose purpose) {
            this.lastMobile = mobileNumber;
            this.lastOtp = otp;
            this.lastPurpose = purpose;
        }
    }
}
