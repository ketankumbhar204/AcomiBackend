package com.acomi.acomi_backend.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.user.domain.model.SystemRole;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminSecurityConfigTest {

    @Test
    void adminEndpointsRequireAdminRole() throws Exception {
        Path source = Path.of("src/main/java/com/acomi/acomi_backend/config/security/SecurityConfig.java");
        String content = Files.readString(source, StandardCharsets.UTF_8);
        assertThat(content).contains("/api/v1/admin/**");
        assertThat(content).contains("ROLE_ADMIN");
    }

    @Test
    void adminRegistrationDeleteEndpointsAreUnderAdminPath() throws Exception {
        Path propertyController =
                Path.of("src/main/java/com/acomi/acomi_backend/admin/api/controller/AdminPropertyRegistrationController.java");
        Path messController =
                Path.of("src/main/java/com/acomi/acomi_backend/admin/api/controller/AdminMessRegistrationController.java");

        assertThat(Files.readString(propertyController, StandardCharsets.UTF_8))
                .contains("@DeleteMapping(\"/{id}\")")
                .contains("/api/v1/admin/property-registrations");
        assertThat(Files.readString(messController, StandardCharsets.UTF_8))
                .contains("@DeleteMapping(\"/{id}\")")
                .contains("/api/v1/admin/mess-registrations");
    }

    @Test
    void registeredUsersEndpointIsUnderAdminPath() throws Exception {
        Path controller =
                Path.of("src/main/java/com/acomi/acomi_backend/admin/api/controller/AdminRegisteredUsersController.java");

        assertThat(Files.readString(controller, StandardCharsets.UTF_8))
                .contains("/api/v1/admin/registered-users");
    }

    @Test
    void savedAddressesEndpointIsUnderAdminPath() throws Exception {
        Path controller =
                Path.of("src/main/java/com/acomi/acomi_backend/address/api/controller/AdminSavedAddressController.java");

        assertThat(Files.readString(controller, StandardCharsets.UTF_8))
                .contains("/api/v1/admin/saved-addresses");
    }

    @Test
    void adminUserReceivesAdminAuthority() {
        UserEntity admin = UserEntity.builder()
                .fullName("Admin")
                .mobileNumber("9000000001")
                .systemRole(SystemRole.ADMIN)
                .build();

        UserPrincipal principal = new UserPrincipal(admin);

        assertThat(principal.getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_USER", "ROLE_ADMIN");
        assertThat(principal.isAdmin()).isTrue();
    }

    @Test
    void regularUserDoesNotReceiveAdminAuthority() {
        UserEntity user = UserEntity.builder()
                .fullName("Owner")
                .mobileNumber("9876543210")
                .systemRole(SystemRole.USER)
                .build();

        UserPrincipal principal = new UserPrincipal(user);

        assertThat(principal.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_USER");
        assertThat(principal.isAdmin()).isFalse();
    }
}
