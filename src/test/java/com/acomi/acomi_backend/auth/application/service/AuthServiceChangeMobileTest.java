package com.acomi.acomi_backend.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.auth.api.dto.request.LoginRequest;
import com.acomi.acomi_backend.auth.api.dto.request.OtpVerifiedActionRequest;
import com.acomi.acomi_backend.auth.api.dto.request.SendOtpRequest;
import com.acomi.acomi_backend.auth.api.dto.request.VerifyOtpRequest;
import com.acomi.acomi_backend.auth.application.otp.OtpDispatchResult;
import com.acomi.acomi_backend.auth.application.otp.RegistrationVerification;
import com.acomi.acomi_backend.auth.domain.model.OtpPurpose;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.config.security.JwtService;
import com.acomi.acomi_backend.config.security.UserPrincipal;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberDocumentRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceChangeMobileTest {

    private static final String CURRENT = "9876543210";
    private static final String NEW_MOBILE = "9123456789";

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
    private UUID userId;
    private UserEntity user;

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
        userId = UUID.randomUUID();
        user = UserEntity.builder()
                .mobileNumber(CURRENT)
                .fullName("Priya Sharma")
                .passwordHash(passwordEncoder.encode("Secret12"))
                .build();
        user.setId(userId);
        authenticateAs(user);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sendOtp_changeMobile_requiresAuthentication() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> authService.sendOtp(sendRequest(NEW_MOBILE), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(otpService, never()).sendOtp(any(), any(), any(), anyBoolean());
    }

    @Test
    void sendOtp_changeMobile_dispatchesToNewNumber() {
        when(userRepository.findByIdAndIsActiveTrue(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByMobileNumberAndIsActiveTrue(NEW_MOBILE)).thenReturn(Optional.empty());
        when(otpService.sendOtp(NEW_MOBILE, OtpPurpose.CHANGE_MOBILE, "127.0.0.1", true))
                .thenReturn(new OtpDispatchResult(300, 60));

        var response = authService.sendOtp(sendRequest(NEW_MOBILE), "127.0.0.1");

        assertThat(response.getMobileNumber()).isEqualTo(NEW_MOBILE);
        assertThat(response.getPurpose()).isEqualTo(OtpPurpose.CHANGE_MOBILE);
        assertThat(user.getMobileNumber()).isEqualTo(CURRENT);
        verify(otpService).sendOtp(NEW_MOBILE, OtpPurpose.CHANGE_MOBILE, "127.0.0.1", true);
        verify(userRepository, never()).save(any());
    }

    @Test
    void sendOtp_changeMobile_rejectsCurrentNumber() {
        when(userRepository.findByIdAndIsActiveTrue(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.sendOtp(sendRequest(CURRENT), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Enter a different mobile number.");

        verify(otpService, never()).sendOtp(any(), any(), any(), anyBoolean());
    }

    @Test
    void sendOtp_changeMobile_rejectsActiveUsersNumber() {
        when(userRepository.findByIdAndIsActiveTrue(userId)).thenReturn(Optional.of(user));
        UserEntity other = UserEntity.builder().mobileNumber(NEW_MOBILE).fullName("Other").build();
        other.setId(UUID.randomUUID());
        when(userRepository.findByMobileNumberAndIsActiveTrue(NEW_MOBILE)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> authService.sendOtp(sendRequest(NEW_MOBILE), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("This mobile number is already registered.")
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(otpService, never()).sendOtp(any(), any(), any(), anyBoolean());
    }

    @Test
    void verifyOtp_changeMobile_doesNotUpdateMobile() {
        when(userRepository.findByIdAndIsActiveTrue(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByMobileNumberAndIsActiveTrue(NEW_MOBILE)).thenReturn(Optional.empty());
        when(otpService.verifyAndIssueToken(NEW_MOBILE, "123456", OtpPurpose.CHANGE_MOBILE, userId))
                .thenReturn(new RegistrationVerification("change-token", 600));

        var response = authService.verifyOtp(verifyRequest(NEW_MOBILE, "123456"));

        assertThat(response.isVerified()).isTrue();
        assertThat(response.getVerificationToken()).isEqualTo("change-token");
        assertThat(user.getMobileNumber()).isEqualTo(CURRENT);
        verify(userRepository, never()).save(any());
    }

    @Test
    void changeMobile_updatesNumberAfterValidToken() {
        when(userRepository.findByIdAndIsActiveTrue(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByMobileNumberAndIsActiveTrue(NEW_MOBILE)).thenReturn(Optional.empty());
        when(userRepository.save(user)).thenReturn(user);
        when(memberRepository.findActiveByUserId(userId)).thenReturn(List.of());
        when(jwtService.generateToken(user)).thenReturn("new-jwt");
        when(jwtService.getExpirationMs()).thenReturn(86_400_000L);

        var response = authService.changeMobile(actionRequest(NEW_MOBILE, "change-token"));

        verify(otpService)
                .consumeVerificationToken(NEW_MOBILE, "change-token", OtpPurpose.CHANGE_MOBILE, userId);
        assertThat(user.getMobileNumber()).isEqualTo(NEW_MOBILE);
        assertThat(user.getMobileVerifiedAt()).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("new-jwt");
        assertThat(response.getUser().getMobileNumber()).isEqualTo(NEW_MOBILE);
    }

    @Test
    void changeMobile_wrongPurposeToken_fails() {
        when(userRepository.findByIdAndIsActiveTrue(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByMobileNumberAndIsActiveTrue(NEW_MOBILE)).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new BusinessException(OtpService.INVALID_VERIFICATION_TOKEN_MESSAGE))
                .when(otpService)
                .consumeVerificationToken(NEW_MOBILE, "register-token", OtpPurpose.CHANGE_MOBILE, userId);

        assertThatThrownBy(() -> authService.changeMobile(actionRequest(NEW_MOBILE, "register-token")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(OtpService.INVALID_VERIFICATION_TOKEN_MESSAGE);

        assertThat(user.getMobileNumber()).isEqualTo(CURRENT);
        verify(userRepository, never()).save(any());
    }

    @Test
    void changeMobile_expiredToken_fails() {
        when(userRepository.findByIdAndIsActiveTrue(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByMobileNumberAndIsActiveTrue(NEW_MOBILE)).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new BusinessException(OtpService.EXPIRED_VERIFICATION_TOKEN_MESSAGE))
                .when(otpService)
                .consumeVerificationToken(NEW_MOBILE, "expired-token", OtpPurpose.CHANGE_MOBILE, userId);

        assertThatThrownBy(() -> authService.changeMobile(actionRequest(NEW_MOBILE, "expired-token")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(OtpService.EXPIRED_VERIFICATION_TOKEN_MESSAGE);

        assertThat(user.getMobileNumber()).isEqualTo(CURRENT);
    }

    @Test
    void changeMobile_otherUsersNumber_isConflict() {
        when(userRepository.findByIdAndIsActiveTrue(userId)).thenReturn(Optional.of(user));
        UserEntity other = UserEntity.builder().mobileNumber(NEW_MOBILE).fullName("Other").build();
        other.setId(UUID.randomUUID());
        when(userRepository.findByMobileNumberAndIsActiveTrue(NEW_MOBILE)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> authService.changeMobile(actionRequest(NEW_MOBILE, "change-token")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("This mobile number is already registered.");

        verify(otpService, never()).consumeVerificationToken(any(), any(), any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void changeMobile_raceOnUniqueIndex_isConflict() {
        when(userRepository.findByIdAndIsActiveTrue(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByMobileNumberAndIsActiveTrue(NEW_MOBILE)).thenReturn(Optional.empty());
        when(userRepository.save(user)).thenThrow(new DataIntegrityViolationException("uk_users_mobile_number_active"));

        assertThatThrownBy(() -> authService.changeMobile(actionRequest(NEW_MOBILE, "change-token")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("This mobile number is already registered.")
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void changeMobile_keepsLinkedMembersAndUpdatesTheirMobile() {
        MemberEntity member = MemberEntity.builder()
                .fullName("Priya Sharma")
                .mobileNumber(CURRENT)
                .build();
        when(userRepository.findByIdAndIsActiveTrue(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByMobileNumberAndIsActiveTrue(NEW_MOBILE)).thenReturn(Optional.empty());
        when(userRepository.save(user)).thenReturn(user);
        when(memberRepository.findActiveByUserId(userId)).thenReturn(List.of(member));
        when(memberRepository.save(member)).thenReturn(member);
        when(jwtService.generateToken(user)).thenReturn("new-jwt");
        when(jwtService.getExpirationMs()).thenReturn(86_400_000L);

        authService.changeMobile(actionRequest(NEW_MOBILE, "change-token"));

        assertThat(member.getMobileNumber()).isEqualTo(NEW_MOBILE);
        assertThat(member.getFullName()).isEqualTo("Priya Sharma");
        verify(memberRepository).save(member);
    }

    @Test
    void oldMobileNoLongerAuthenticatesAfterChange() {
        user.setMobileNumber(NEW_MOBILE);
        when(userRepository.findByMobileNumberAndIsActiveTrue(CURRENT)).thenReturn(Optional.empty());

        LoginRequest login = new LoginRequest();
        login.setMobileNumber(CURRENT);
        login.setPassword("Secret12");

        assertThatThrownBy(() -> authService.login(login))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid mobile number or password.");
    }

    @Test
    void newMobileAuthenticatesAfterChange() {
        user.setMobileNumber(NEW_MOBILE);
        when(userRepository.findByMobileNumberAndIsActiveTrue(NEW_MOBILE)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("jwt");
        when(jwtService.getExpirationMs()).thenReturn(86_400_000L);

        LoginRequest login = new LoginRequest();
        login.setMobileNumber(NEW_MOBILE);
        login.setPassword("Secret12");

        var response = authService.login(login);
        assertThat(response.getUser().getMobileNumber()).isEqualTo(NEW_MOBILE);
        assertThat(response.getAccessToken()).isEqualTo("jwt");
    }

    @Test
    void sendOtp_loginStillWorksDuringPendingChange() {
        when(otpService.sendOtp(eq(CURRENT), eq(OtpPurpose.LOGIN), eq("127.0.0.1"), eq(true)))
                .thenReturn(new OtpDispatchResult(300, 60));
        when(userRepository.findByMobileNumberAndIsActiveTrue(CURRENT)).thenReturn(Optional.of(user));

        SendOtpRequest request = new SendOtpRequest();
        request.setMobileNumber(CURRENT);
        request.setPurpose(OtpPurpose.LOGIN);

        var response = authService.sendOtp(request, "127.0.0.1");
        assertThat(response.getPurpose()).isEqualTo(OtpPurpose.LOGIN);
        assertThat(user.getMobileNumber()).isEqualTo(CURRENT);
    }

    @Test
    void changeMobile_doesNotCallOtpServiceUntilEligible() {
        when(userRepository.findByIdAndIsActiveTrue(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.changeMobile(actionRequest(CURRENT, "token")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Enter a different mobile number.");

        verify(otpService, never()).consumeVerificationToken(any(), any(), any(), any());
    }

    @Test
    void changeMobile_tokenForDifferentMobile_isRejectedByOtpService() {
        when(userRepository.findByIdAndIsActiveTrue(userId)).thenReturn(Optional.of(user));
        when(userRepository.findByMobileNumberAndIsActiveTrue(NEW_MOBILE)).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new BusinessException(OtpService.INVALID_VERIFICATION_TOKEN_MESSAGE))
                .when(otpService)
                .consumeVerificationToken(NEW_MOBILE, "other-mobile-token", OtpPurpose.CHANGE_MOBILE, userId);

        assertThatThrownBy(() -> authService.changeMobile(actionRequest(NEW_MOBILE, "other-mobile-token")))
                .isInstanceOf(BusinessException.class)
                .hasMessage(OtpService.INVALID_VERIFICATION_TOKEN_MESSAGE);
        assertThat(user.getMobileNumber()).isEqualTo(CURRENT);
    }

    private void authenticateAs(UserEntity entity) {
        UserPrincipal principal = new UserPrincipal(entity);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities()));
    }

    private static SendOtpRequest sendRequest(String mobile) {
        SendOtpRequest request = new SendOtpRequest();
        request.setMobileNumber(mobile);
        request.setPurpose(OtpPurpose.CHANGE_MOBILE);
        return request;
    }

    private static VerifyOtpRequest verifyRequest(String mobile, String otp) {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setMobileNumber(mobile);
        request.setOtp(otp);
        request.setPurpose(OtpPurpose.CHANGE_MOBILE);
        return request;
    }

    private static OtpVerifiedActionRequest actionRequest(String mobile, String token) {
        OtpVerifiedActionRequest request = new OtpVerifiedActionRequest();
        ReflectionTestUtils.setField(request, "mobileNumber", mobile);
        ReflectionTestUtils.setField(request, "verificationToken", token);
        return request;
    }
}
