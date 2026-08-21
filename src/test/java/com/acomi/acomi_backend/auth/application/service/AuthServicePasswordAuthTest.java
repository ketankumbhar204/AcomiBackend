package com.acomi.acomi_backend.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.auth.api.dto.request.LoginRequest;
import com.acomi.acomi_backend.auth.api.dto.request.RegisterRequest;
import com.acomi.acomi_backend.auth.api.dto.request.SendOtpRequest;
import com.acomi.acomi_backend.auth.api.dto.request.VerifyOtpRequest;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.config.security.JwtService;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberDocumentRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServicePasswordAuthTest {

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

    private final PasswordEncoder passwordEncoder =
            PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                otpService,
                jwtService,
                userRepository,
                memberRepository,
                memberDocumentRepository,
                accountDeletionService,
                passwordEncoder);
    }

    @Test
    void register_hashesPasswordAndReturnsJwt() {
        when(userRepository.findByMobileNumberAndIsActiveTrue("9876543210"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity created = invocation.getArgument(0);
            created.setId(UUID.randomUUID());
            return created;
        });
        when(jwtService.generateToken(any(UserEntity.class))).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(86_400_000L);

        var response = authService.register(registerRequest(
                "Priya Sharma", "9876543210", "Secret12", "Secret12", "verification-token"));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(otpService).consumeRegistrationVerificationToken("9876543210", "verification-token");
        verify(userRepository).save(captor.capture());
        UserEntity saved = captor.getValue();
        assertThat(saved.getPasswordHash()).isNotBlank();
        assertThat(saved.getPasswordHash()).isNotEqualTo("Secret12");
        assertThat(saved.getPasswordHash()).doesNotContain("Secret12");
        assertThat(passwordEncoder.matches("Secret12", saved.getPasswordHash())).isTrue();
        assertThat(saved.getMobileVerifiedAt()).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("jwt-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getUser().getFullName()).isEqualTo("Priya Sharma");
        assertThat(response.getUser().getMobileNumber()).isEqualTo("9876543210");
        assertThat(response.getUser().getFullName()).doesNotContain("Secret12");
    }

    @Test
    void register_duplicateMobile_isConflict() {
        when(userRepository.findByMobileNumberAndIsActiveTrue("9876543210"))
                .thenReturn(Optional.of(UserEntity.builder()
                        .mobileNumber("9876543210")
                        .fullName("Existing")
                        .build()));

        assertThatThrownBy(() -> authService.register(registerRequest(
                        "Priya Sharma", "9876543210", "Secret12", "Secret12", "verification-token")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("This mobile number is already registered.")
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(userRepository, never()).save(any());
        verify(otpService, never()).consumeRegistrationVerificationToken(any(), any());
    }

    @Test
    void register_passwordMismatch_isRejected() {
        assertThatThrownBy(() -> authService.register(registerRequest(
                        "Priya Sharma", "9876543210", "Secret12", "Secret99", "verification-token")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Passwords do not match");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_withoutVerificationToken_hashesPasswordAndReturnsJwt() {
        when(userRepository.findByMobileNumberAndIsActiveTrue("9876543210"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity created = invocation.getArgument(0);
            created.setId(UUID.randomUUID());
            return created;
        });
        when(jwtService.generateToken(any(UserEntity.class))).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(86_400_000L);

        var response = authService.register(registerRequest(
                "Priya Sharma", "9876543210", "Secret12", "Secret12", null));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(otpService, never()).consumeRegistrationVerificationToken(any(), any());
        verify(userRepository).save(captor.capture());
        UserEntity saved = captor.getValue();
        assertThat(saved.getPasswordHash()).isNotEqualTo("Secret12");
        assertThat(passwordEncoder.matches("Secret12", saved.getPasswordHash())).isTrue();
        assertThat(saved.getMobileVerifiedAt()).isNull();
        assertThat(response.getAccessToken()).isEqualTo("jwt-token");
        assertThat(response.getUser().getFullName()).isEqualTo("Priya Sharma");
    }

    @Test
    void register_invalidVerificationToken_isRejected() {
        org.mockito.Mockito.doThrow(new BusinessException("Invalid or expired verification token."))
                .when(otpService)
                .consumeRegistrationVerificationToken("9876543210", "bad-token");

        assertThatThrownBy(() -> authService.register(registerRequest(
                        "Priya Sharma", "9876543210", "Secret12", "Secret12", "bad-token")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid or expired verification token.");

        verify(userRepository, never()).save(any());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void register_expiredVerificationToken_isRejected() {
        org.mockito.Mockito.doThrow(new BusinessException("Verification token has expired. Request a new OTP."))
                .when(otpService)
                .consumeRegistrationVerificationToken("9876543210", "expired-token");

        assertThatThrownBy(() -> authService.register(registerRequest(
                        "Priya Sharma", "9876543210", "Secret12", "Secret12", "expired-token")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Verification token has expired. Request a new OTP.");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_consumedVerificationToken_isRejected() {
        org.mockito.Mockito.doThrow(new BusinessException("This verification token has already been used."))
                .when(otpService)
                .consumeRegistrationVerificationToken("9876543210", "used-token");

        assertThatThrownBy(() -> authService.register(registerRequest(
                        "Priya Sharma", "9876543210", "Secret12", "Secret12", "used-token")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("This verification token has already been used.");

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_validPassword_returnsJwt() {
        UserEntity user = activeUserWithPassword("9876543210", "Secret12");
        when(userRepository.findByMobileNumberAndIsActiveTrue("9876543210"))
                .thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(86_400_000L);

        var response = authService.login(loginRequest("9876543210", "Secret12"));

        assertThat(response.getAccessToken()).isEqualTo("jwt-token");
        assertThat(response.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    void login_invalidPassword_doesNotRevealAccount() {
        UserEntity user = activeUserWithPassword("9876543210", "Secret12");
        when(userRepository.findByMobileNumberAndIsActiveTrue("9876543210"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(loginRequest("9876543210", "WrongPass")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid mobile number or password.")
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void login_unknownMobile_usesSameErrorAsInvalidPassword() {
        when(userRepository.findByMobileNumberAndIsActiveTrue("9876543210"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest("9876543210", "Secret12")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid mobile number or password.");

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void login_inactiveUser_cannotAuthenticate() {
        when(userRepository.findByMobileNumberAndIsActiveTrue("9876543210"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest("9876543210", "Secret12")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid mobile number or password.");
    }

    @Test
    void login_otpOnlyUserWithoutPassword_cannotUsePasswordLogin() {
        UserEntity otpUser = UserEntity.builder()
                .mobileNumber("9876543210")
                .fullName("OTP User")
                .isActive(true)
                .build();
        otpUser.setId(UUID.randomUUID());
        when(userRepository.findByMobileNumberAndIsActiveTrue("9876543210"))
                .thenReturn(Optional.of(otpUser));

        assertThatThrownBy(() -> authService.login(loginRequest("9876543210", "111111")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid mobile number or password.");

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void login_developmentOtpCode_doesNotBypassPassword() {
        UserEntity user = activeUserWithPassword("9876543210", "Secret12");
        when(userRepository.findByMobileNumberAndIsActiveTrue("9876543210"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(loginRequest("9876543210", "111111")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid mobile number or password.");
    }

    @Test
    void sendOtp_register_duplicateActiveMobile_isConflict() {
        when(userRepository.findByMobileNumberAndIsActiveTrue("9876543210"))
                .thenReturn(Optional.of(UserEntity.builder()
                        .mobileNumber("9876543210")
                        .fullName("Existing")
                        .build()));

        SendOtpRequest request = new SendOtpRequest();
        request.setMobileNumber("9876543210");
        request.setPurpose(com.acomi.acomi_backend.auth.domain.model.OtpPurpose.REGISTER);

        assertThatThrownBy(() -> authService.sendOtp(request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("This mobile number is already registered.")
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(otpService, never()).sendOtp(any(), any(), any());
    }

    @Test
    void sendOtp_register_newMobile_dispatchesOtp() {
        when(userRepository.findByMobileNumberAndIsActiveTrue("9876543210"))
                .thenReturn(Optional.empty());
        when(otpService.sendOtp(
                        "9876543210",
                        com.acomi.acomi_backend.auth.domain.model.OtpPurpose.REGISTER,
                        "127.0.0.1"))
                .thenReturn(new com.acomi.acomi_backend.auth.application.otp.OtpDispatchResult(300, 60));

        SendOtpRequest request = new SendOtpRequest();
        request.setMobileNumber("9876543210");
        request.setPurpose(com.acomi.acomi_backend.auth.domain.model.OtpPurpose.REGISTER);

        var response = authService.sendOtp(request, "127.0.0.1");

        assertThat(response.getPurpose())
                .isEqualTo(com.acomi.acomi_backend.auth.domain.model.OtpPurpose.REGISTER);
        assertThat(response.getExpiresIn()).isEqualTo(300);
        assertThat(response.getResendAfter()).isEqualTo(60);
        verify(otpService)
                .sendOtp(
                        "9876543210",
                        com.acomi.acomi_backend.auth.domain.model.OtpPurpose.REGISTER,
                        "127.0.0.1");
    }

    @Test
    void verifyOtp_returnsVerificationTokenWithoutCreatingUserOrIssuingJwt() {
        when(userRepository.findByMobileNumberAndIsActiveTrue("9876543210"))
                .thenReturn(Optional.empty());
        when(otpService.verifyRegistrationOtp("9876543210", "482731"))
                .thenReturn(new com.acomi.acomi_backend.auth.application.otp.RegistrationVerification(
                        "registration-token", 600));

        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setMobileNumber("9876543210");
        request.setOtp("482731");
        request.setPurpose(com.acomi.acomi_backend.auth.domain.model.OtpPurpose.REGISTER);

        var response = authService.verifyOtp(request);

        assertThat(response.isVerified()).isTrue();
        assertThat(response.getVerificationToken()).isEqualTo("registration-token");
        assertThat(response.getExpiresIn()).isEqualTo(600);
        verify(userRepository, never()).save(any());
        verify(jwtService, never()).generateToken(any());
    }

    private UserEntity activeUserWithPassword(String mobile, String rawPassword) {
        UserEntity user = UserEntity.builder()
                .mobileNumber(mobile)
                .fullName("Priya Sharma")
                .passwordHash(passwordEncoder.encode(rawPassword))
                .isActive(true)
                .build();
        user.setId(UUID.randomUUID());
        return user;
    }

    private static RegisterRequest registerRequest(
            String fullName, String mobile, String password, String confirmPassword, String verificationToken) {
        RegisterRequest request = new RegisterRequest();
        request.setFullName(fullName);
        request.setMobileNumber(mobile);
        request.setPassword(password);
        request.setConfirmPassword(confirmPassword);
        request.setVerificationToken(verificationToken);
        return request;
    }

    private static LoginRequest loginRequest(String mobile, String password) {
        LoginRequest request = new LoginRequest();
        request.setMobileNumber(mobile);
        request.setPassword(password);
        return request;
    }
}
