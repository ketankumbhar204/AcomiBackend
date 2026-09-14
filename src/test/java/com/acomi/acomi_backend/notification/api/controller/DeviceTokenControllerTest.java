package com.acomi.acomi_backend.notification.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acomi.acomi_backend.notification.api.dto.request.RegisterDeviceTokenRequest;
import com.acomi.acomi_backend.notification.api.dto.response.DeviceTokenResponse;
import com.acomi.acomi_backend.notification.application.service.DeviceTokenService;
import com.acomi.acomi_backend.notification.domain.model.DevicePlatform;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DeviceTokenControllerTest {

    @Mock
    private DeviceTokenService deviceTokenService;

    private MockMvc mockMvc;
    private UUID userId;

    @BeforeEach
    void setUp() {
        var validator = new org.springframework.validation.beanvalidation.LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new DeviceTokenController(deviceTokenService))
                .setValidator(validator)
                .build();
        userId = UUID.randomUUID();
    }

    @Test
    void registerUsesAuthenticatedUser() throws Exception {
        DeviceTokenResponse response = DeviceTokenResponse.builder()
                .deviceTokenId(UUID.randomUUID())
                .deviceId("device-1")
                .platform(DevicePlatform.ANDROID)
                .active(true)
                .build();
        Mockito.when(deviceTokenService.register(eq(userId), any(RegisterDeviceTokenRequest.class)))
                .thenReturn(response);

        try (MockedStatic<com.acomi.acomi_backend.common.security.SecurityUtils> security =
                Mockito.mockStatic(com.acomi.acomi_backend.common.security.SecurityUtils.class)) {
            security.when(com.acomi.acomi_backend.common.security.SecurityUtils::getCurrentUserId).thenReturn(userId);
            mockMvc.perform(post("/api/v1/notifications/devices")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    "{\"token\":\"fcm-token\",\"platform\":\"ANDROID\",\"deviceId\":\"device-1\",\"appVersion\":\"1.0\"}"))
                    .andExpect(status().isOk());
        }

        verify(deviceTokenService).register(eq(userId), any(RegisterDeviceTokenRequest.class));
    }

    @Test
    void registerRejectsMissingToken() throws Exception {
        try (MockedStatic<com.acomi.acomi_backend.common.security.SecurityUtils> security =
                Mockito.mockStatic(com.acomi.acomi_backend.common.security.SecurityUtils.class)) {
            security.when(com.acomi.acomi_backend.common.security.SecurityUtils::getCurrentUserId).thenReturn(userId);
            mockMvc.perform(post("/api/v1/notifications/devices")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"platform\":\"ANDROID\",\"deviceId\":\"device-1\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void deactivateUsesAuthenticatedUserAndDeviceId() throws Exception {
        try (MockedStatic<com.acomi.acomi_backend.common.security.SecurityUtils> security =
                Mockito.mockStatic(com.acomi.acomi_backend.common.security.SecurityUtils.class)) {
            security.when(com.acomi.acomi_backend.common.security.SecurityUtils::getCurrentUserId).thenReturn(userId);
            mockMvc.perform(delete("/api/v1/notifications/devices/device-1")).andExpect(status().isOk());
        }
        verify(deviceTokenService).deactivateCurrentDevice(userId, "device-1");
    }
}
