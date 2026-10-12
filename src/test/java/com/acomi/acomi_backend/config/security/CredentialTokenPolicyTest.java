package com.acomi.acomi_backend.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import org.junit.jupiter.api.Test;

class CredentialTokenPolicyTest {

    @Test
    void tokenIssuedBeforePasswordChangeIsRejected() {
        LocalDateTime changedAt = LocalDateTime.of(2026, 10, 12, 12, 0);
        Date before = Date.from(changedAt.minusMinutes(1).atZone(ZoneId.systemDefault()).toInstant());
        Date after = Date.from(changedAt.plusMinutes(1).atZone(ZoneId.systemDefault()).toInstant());

        assertThat(JwtService.tokenPredatesCredentialChange(before, changedAt)).isTrue();
        assertThat(JwtService.tokenPredatesCredentialChange(after, changedAt)).isFalse();
        assertThat(JwtService.tokenPredatesCredentialChange(before, null)).isFalse();
        assertThat(JwtService.tokenPredatesCredentialChange(null, changedAt)).isFalse();
    }
}
