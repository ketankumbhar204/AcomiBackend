package com.countin.countin_backend.auth.api.controller;

import com.countin.countin_backend.auth.api.dto.request.SendOtpRequest;
import com.countin.countin_backend.auth.api.dto.request.VerifyOtpRequest;
import com.countin.countin_backend.auth.api.dto.response.AuthTokenResponse;
import com.countin.countin_backend.auth.api.dto.response.SendOtpResponse;
import com.countin.countin_backend.auth.application.service.AuthService;
import com.countin.countin_backend.common.web.ApiResponse;
import com.countin.countin_backend.user.api.dto.request.CompleteUserProfileRequest;
import com.countin.countin_backend.user.api.dto.request.UpdateUserRequest;
import com.countin.countin_backend.user.api.dto.response.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<SendOtpResponse>> sendOtp(
            @RequestBody @Valid SendOtpRequest request) {
        SendOtpResponse response = authService.sendOtp(request);
        return ResponseEntity.ok(ApiResponse.success("OTP sent successfully", response));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> verifyOtp(
            @RequestBody @Valid VerifyOtpRequest request) {
        AuthTokenResponse response = authService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
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
}
