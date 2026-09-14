package com.acomi.acomi_backend.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DeviceTokenSecurityConfigTest {

    @Test
    void deviceTokenApiIsAuthenticatedAndCurrentUserScoped() throws Exception {
        Path controller = Path.of(
                "src/main/java/com/acomi/acomi_backend/notification/api/controller/DeviceTokenController.java");
        String content = Files.readString(controller, StandardCharsets.UTF_8);
        assertThat(content).contains("/api/v1/notifications/devices");
        assertThat(content).contains("SecurityUtils.getCurrentUserId()");
        assertThat(content).doesNotContain("setUserId");
        assertThat(content).doesNotContain("request.getUserId");
    }

    @Test
    void deviceTokenApiIsNotPublic() throws Exception {
        Path security = Path.of("src/main/java/com/acomi/acomi_backend/config/security/SecurityConfig.java");
        String content = Files.readString(security, StandardCharsets.UTF_8);
        assertThat(content).doesNotContain("/api/v1/notifications/devices");
        assertThat(content).contains(".anyRequest()");
        assertThat(content).contains(".authenticated()");
    }
}
