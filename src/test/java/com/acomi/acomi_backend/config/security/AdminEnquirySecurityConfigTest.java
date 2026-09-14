package com.acomi.acomi_backend.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminEnquirySecurityConfigTest {

    @Test
    void adminEnquiryEndpointsAreUnderAdminPath() throws Exception {
        Path controller = Path.of(
                "src/main/java/com/acomi/acomi_backend/admin/api/controller/AdminSpaceEnquiryController.java");
        String content = Files.readString(controller, StandardCharsets.UTF_8);
        assertThat(content).contains("/api/v1/admin/enquiries");
        assertThat(content).contains("/{enquiryId}/share");
        assertThat(content).contains("/{enquiryId}/reject");
        assertThat(content).contains("/{enquiryId}/expire");
    }

    @Test
    void memberEnquiryControllerDoesNotExposeOwnerContact() throws Exception {
        Path controller = Path.of(
                "src/main/java/com/acomi/acomi_backend/enquiry/api/controller/SpaceEnquiryController.java");
        String content = Files.readString(controller, StandardCharsets.UTF_8);
        assertThat(content).doesNotContain("OwnerContact");
        assertThat(content).doesNotContain("/owner-contact");
        assertThat(content).contains("/enquiries/me");
    }
}
