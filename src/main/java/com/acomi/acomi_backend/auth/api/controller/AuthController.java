package com.acomi.acomi_backend.auth.api.controller;

import com.acomi.acomi_backend.auth.api.dto.request.LoginRequest;
import com.acomi.acomi_backend.auth.api.dto.request.OtpVerifiedActionRequest;
import com.acomi.acomi_backend.auth.api.dto.request.PasswordAccountDeletionRequest;
import com.acomi.acomi_backend.auth.api.dto.request.RegisterRequest;
import com.acomi.acomi_backend.auth.api.dto.request.ResetPasswordRequest;
import com.acomi.acomi_backend.auth.api.dto.request.SendOtpRequest;
import com.acomi.acomi_backend.auth.api.dto.request.VerifyOtpRequest;
import com.acomi.acomi_backend.auth.api.dto.response.AuthTokenResponse;
import com.acomi.acomi_backend.auth.api.dto.response.SendOtpResponse;
import com.acomi.acomi_backend.auth.api.dto.response.VerifyOtpResponse;
import com.acomi.acomi_backend.auth.application.otp.ClientIpResolver;
import com.acomi.acomi_backend.auth.application.service.AuthService;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.user.api.dto.request.CompleteUserProfileRequest;
import com.acomi.acomi_backend.user.api.dto.request.UpdateUserRequest;
import com.acomi.acomi_backend.user.api.dto.response.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> register(
            @RequestBody @Valid RegisterRequest request) {
        AuthTokenResponse response = authService.register(request);
        return ResponseEntity.ok(ApiResponse.success("Account created successfully", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> login(
            @RequestBody @Valid LoginRequest request) {
        AuthTokenResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<SendOtpResponse>> sendOtp(
            @RequestBody @Valid SendOtpRequest request, HttpServletRequest httpRequest) {
        SendOtpResponse response = authService.sendOtp(request, ClientIpResolver.resolve(httpRequest));
        return ResponseEntity.ok(ApiResponse.success("OTP sent successfully", response));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<VerifyOtpResponse>> verifyOtp(
            @RequestBody @Valid VerifyOtpRequest request) {
        VerifyOtpResponse response = authService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.success("OTP verified successfully", response));
    }

    @PostMapping("/login-with-otp")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> loginWithOtp(
            @RequestBody @Valid OtpVerifiedActionRequest request) {
        AuthTokenResponse response = authService.loginWithOtp(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @RequestBody @Valid ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password updated successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        UserResponse response = authService.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateCurrentUser(
            @RequestBody @Valid UpdateUserRequest request) {
        UserResponse response = authService.updateCurrentUser(request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", response));
    }

    @PatchMapping("/me/profile")
    public ResponseEntity<ApiResponse<UserResponse>> completeCurrentUserProfile(
            @RequestBody @Valid CompleteUserProfileRequest request) {
        UserResponse response = authService.completeCurrentUserProfile(request);
        return ResponseEntity.ok(ApiResponse.success("Profile completed successfully", response));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteCurrentAccount() {
        authService.deleteCurrentAccount();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/account-deletion")
    public ResponseEntity<Void> deleteAccountByOtp(
            @RequestBody @Valid OtpVerifiedActionRequest request) {
        authService.deleteAccountByOtp(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/account-deletion/password")
    public ResponseEntity<Void> deleteAccountByPassword(
            @RequestBody @Valid PasswordAccountDeletionRequest request) {
        authService.deleteAccountByPassword(request);
        return ResponseEntity.noContent().build();
    }
}
