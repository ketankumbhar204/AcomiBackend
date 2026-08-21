package com.acomi.acomi_backend.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.auth.api.dto.request.RegisterRequest;
import com.acomi.acomi_backend.config.security.JwtService;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberDocumentRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

@ExtendWith(MockitoExtension.class)
class AuthServiceAccountDeletionTest {

    @Mock
    private OtpService otpService;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberDocumentRepository memberDocumentRepository;

    @Mock
    private AccountDeletionService accountDeletionService;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void register_afterDeletion_createsNewActiveUserForSameMobile() {
        UUID previousUserId = UUID.randomUUID();
        when(userRepository.findByMobileNumberAndIsActiveTrue("9876543210"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity created = invocation.getArgument(0);
            created.setId(UUID.randomUUID());
            return created;
        });
        when(jwtService.generateToken(any(UserEntity.class))).thenReturn("new-token");
        when(jwtService.getExpirationMs()).thenReturn(86_400_000L);
        when(passwordEncoder.encode("Secret12")).thenReturn("{bcrypt}hashed");

        RegisterRequest request = new RegisterRequest();
        request.setFullName("Priya Sharma");
        request.setMobileNumber("9876543210");
        request.setPassword("Secret12");
        request.setConfirmPassword("Secret12");
        request.setVerificationToken("registration-token");

        var response = authService.register(request);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(otpService).consumeRegistrationVerificationToken("9876543210", "registration-token");
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isTrue();
        assertThat(captor.getValue().getMobileNumber()).isEqualTo("9876543210");
        assertThat(captor.getValue().getMobileVerifiedAt()).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("new-token");
        assertThat(response.getUser().getId()).isNotEqualTo(previousUserId);
    }

    @Test
    void deleteCurrentAccount_delegatesToSharedDeletionService() {
        authService.deleteCurrentAccount();
        verify(accountDeletionService).deleteAuthenticatedAccount();
    }
}
