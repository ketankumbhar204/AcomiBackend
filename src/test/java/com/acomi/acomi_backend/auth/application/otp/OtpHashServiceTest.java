package com.acomi.acomi_backend.auth.application.otp;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.auth.domain.model.OtpPurpose;
import com.acomi.acomi_backend.config.security.OtpProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OtpHashServiceTest {

    private OtpHashService hashService;

    @BeforeEach
    void setUp() {
        OtpProperties properties = new OtpProperties();
        properties.setHashSecret("test-otp-hash-secret-must-be-32chars!");
        hashService = new OtpHashService(properties);
    }

    @Test
    void hashOtp_doesNotContainPlaintextCode() {
        String otp = "482731";
        String hash = hashService.hashOtp("9876543210", OtpPurpose.REGISTER, otp);

        assertThat(hash).hasSize(64);
        assertThat(hash).doesNotContain(otp);
        assertThat(hash).doesNotContain("9876543210");
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    void hashOtp_isBoundToMobileAndPurpose() {
        String otp = "482731";
        String registerHash = hashService.hashOtp("9876543210", OtpPurpose.REGISTER, otp);
        String deletionHash = hashService.hashOtp("9876543210", OtpPurpose.ACCOUNT_DELETION, otp);
        String otherMobile = hashService.hashOtp("9123456789", OtpPurpose.REGISTER, otp);

        assertThat(registerHash).isNotEqualTo(deletionHash);
        assertThat(registerHash).isNotEqualTo(otherMobile);
        assertThat(hashService.matches(registerHash, registerHash)).isTrue();
        assertThat(hashService.matches(registerHash, deletionHash)).isFalse();
    }

    @Test
    void hashVerificationToken_doesNotContainRawToken() {
        String token = "super-secret-verification-token-value";
        String hash = hashService.hashVerificationToken("9876543210", OtpPurpose.REGISTER, token);

        String loginHash = hashService.hashVerificationToken("9876543210", OtpPurpose.LOGIN, token);
        String resetHash = hashService.hashVerificationToken("9876543210", OtpPurpose.RESET_PASSWORD, token);
        String deleteHash = hashService.hashVerificationToken("9876543210", OtpPurpose.ACCOUNT_DELETION, token);

        assertThat(hash).isNotEqualTo(loginHash);
        assertThat(loginHash).isNotEqualTo(resetHash);
        assertThat(resetHash).isNotEqualTo(deleteHash);
    }
}
