package com.acomi.acomi_backend.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OtpProductionSafetyTest {

    @Test
    void applicationYamlDoesNotContainUniversalOtp() throws Exception {
        String yaml = Files.readString(
                Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);

        assertThat(yaml).doesNotContain("111111");
        assertThat(yaml).doesNotContain("mvp-code");
        assertThat(yaml).contains("sender: none");
    }

    @Test
    void productionYamlCannotUseDevelopmentOtpSender() throws Exception {
        String yaml = Files.readString(
                Path.of("src/main/resources/application-prod.yml"), StandardCharsets.UTF_8);

        assertThat(yaml).contains("sender: none");
        assertThat(yaml).doesNotContain("sender: dev");
        assertThat(yaml).doesNotContain("mvp-code");
        assertThat(yaml).doesNotContain("111111");
        assertThat(yaml).contains("hash-secret: ${OTP_HASH_SECRET}");
        assertThat(yaml).doesNotContain("twilio");
    }

    @Test
    void localYamlUsesDevelopmentSenderOnly() throws Exception {
        String yaml = Files.readString(
                Path.of("src/main/resources/application-local.yml"), StandardCharsets.UTF_8);

        assertThat(yaml).contains("sender: dev");
        assertThat(yaml).doesNotContain("mvp-code");
    }
}
