package com.acomi.acomi_backend.notification.api.controller;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.notification.api.dto.request.RegisterDeviceTokenRequest;
import com.acomi.acomi_backend.notification.api.dto.response.DeviceTokenResponse;
import com.acomi.acomi_backend.notification.application.service.DeviceTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications/devices")
@RequiredArgsConstructor
@Tag(name = "Device Tokens", description = "Register and deactivate FCM device tokens for the authenticated user")
@SecurityRequirement(name = "bearerAuth")
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    @PostMapping
    @Operation(summary = "Register or refresh this device's FCM token")
    public ResponseEntity<ApiResponse<DeviceTokenResponse>> register(
            @Valid @RequestBody RegisterDeviceTokenRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Device registered", deviceTokenService.register(callerId, request)));
    }

    @DeleteMapping("/{deviceId}")
    @Operation(summary = "Deactivate the current device token (logout)")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable String deviceId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        deviceTokenService.deactivateCurrentDevice(callerId, deviceId);
        return ResponseEntity.ok(ApiResponse.success("Device unregistered", null));
    }
}
