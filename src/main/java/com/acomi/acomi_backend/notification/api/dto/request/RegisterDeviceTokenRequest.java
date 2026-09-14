package com.acomi.acomi_backend.notification.api.dto.request;

import com.acomi.acomi_backend.notification.domain.model.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RegisterDeviceTokenRequest {

    @NotBlank(message = "FCM token is required")
    @Size(max = 4096, message = "FCM token is too long")
    private String token;

    @NotNull(message = "Platform is required")
    private DevicePlatform platform;

    @NotBlank(message = "Device id is required")
    @Size(max = 128, message = "Device id is too long")
    private String deviceId;

    @Size(max = 40, message = "App version is too long")
    private String appVersion;
}
