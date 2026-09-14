package com.acomi.acomi_backend.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UserNotificationSecurityConfigTest {

    @Test
    void requesterNotificationApiIsAuthenticatedCurrentUserScoped() throws Exception {
        Path controller = Path.of(
                "src/main/java/com/acomi/acomi_backend/notification/api/controller/UserNotificationController.java");
        String content = Files.readString(controller, StandardCharsets.UTF_8);
        assertThat(content).contains("/api/v1/notifications");
        assertThat(content).contains("/me");
        assertThat(content).contains("SecurityUtils.getCurrentUserId()");
        assertThat(content).doesNotContain("@RequestParam");
        assertThat(content).doesNotContain("OwnerContact");
        assertThat(content).doesNotContain("/admin/");
    }

    @Test
    void requesterNotificationApiIsNotPublic() throws Exception {
        Path security = Path.of("src/main/java/com/acomi/acomi_backend/config/security/SecurityConfig.java");
        String content = Files.readString(security, StandardCharsets.UTF_8);
        assertThat(content).doesNotContain("\"/api/v1/notifications");
        assertThat(content).contains(".anyRequest()");
        assertThat(content).contains(".authenticated()");
    }
}
