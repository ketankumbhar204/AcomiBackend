package com.acomi.acomi_backend.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SecurityConfigAccountDeletionTest {

    @Test
    void deleteAccountEndpointIsNotPublic() throws Exception {
        Path source = Path.of("src/main/java/com/acomi/acomi_backend/config/security/SecurityConfig.java");
        String content = Files.readString(source, StandardCharsets.UTF_8);

        assertThat(content).contains("/api/v1/auth/send-otp");
        assertThat(content).contains("/api/v1/auth/verify-otp");
        assertThat(content).contains("/api/v1/auth/account-deletion");
        assertThat(content).doesNotContain("\"/api/v1/auth/me\"");
        assertThat(content).doesNotContain("/api/v1/auth/change-mobile");
    }
}
