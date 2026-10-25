package com.acomi.acomi_backend.registration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.config.security.UserPrincipal;
import com.acomi.acomi_backend.user.domain.model.SystemRole;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class RegistrationOwnerLinkerTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RegistrationOwnerLinker linker;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void linksTheSingleActiveEligibleAccount() {
        UserEntity owner = user("9876543210", true, SystemRole.USER);
        when(userRepository.findByNationalMobile("9876543210")).thenReturn(List.of(owner));

        assertThat(linker.linkForVerifiedMobile("+91 98765 43210")).isEqualTo(owner.getId());
    }

    @Test
    void leavesUnmatchedMobilesUnlinked() {
        when(userRepository.findByNationalMobile("9876543210")).thenReturn(List.of());

        assertThat(linker.linkForVerifiedMobile("9876543210")).isNull();
    }

    @Test
    void leavesInactiveAndAdminAccountsUnlinked() {
        when(userRepository.findByNationalMobile("9876543210"))
                .thenReturn(List.of(user("9876543210", false, SystemRole.USER), user("9876543210", true, SystemRole.ADMIN)));

        assertThat(linker.linkForVerifiedMobile("9876543210")).isNull();
    }

    @Test
    void doesNotPickWhenSeveralEligibleAccountsMatch() {
        when(userRepository.findByNationalMobile("9876543210"))
                .thenReturn(List.of(user("9876543210", true, SystemRole.USER), user("9876543210", true, SystemRole.USER)));

        assertThat(linker.linkForVerifiedMobile("9876543210")).isNull();
    }

    @Test
    void doesNotAttachSignedInUserWhenVerifiedMobileDiffers() {
        UserEntity signedIn = user("9000000000", true, SystemRole.USER);
        UserEntity otpMatch = user("9876543210", true, SystemRole.USER);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(new UserPrincipal(signedIn), null));
        when(userRepository.findByNationalMobile("9876543210")).thenReturn(List.of(otpMatch));

        assertThat(linker.linkForVerifiedMobile("9876543210")).isEqualTo(otpMatch.getId());
        assertThat(linker.linkForVerifiedMobile("9876543210")).isNotEqualTo(signedIn.getId());
    }

    @Test
    void loggedOutOtpStillLinksTheMatchingAccount() {
        UserEntity owner = user("9876543210", true, SystemRole.USER);
        when(userRepository.findByNationalMobile("9876543210")).thenReturn(List.of(owner));

        assertThat(linker.linkForVerifiedMobile("9876543210")).isEqualTo(owner.getId());
    }

    private static UserEntity user(String mobile, boolean active, SystemRole role) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setMobileNumber(mobile);
        user.setActive(active);
        user.setSystemRole(role);
        return user;
    }
}
