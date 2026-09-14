package com.acomi.acomi_backend.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.notification.api.dto.request.RegisterDeviceTokenRequest;
import com.acomi.acomi_backend.notification.domain.model.DevicePlatform;
import com.acomi.acomi_backend.notification.infrastructure.persistence.entity.UserDeviceTokenEntity;
import com.acomi.acomi_backend.notification.infrastructure.persistence.repository.UserDeviceTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-12T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private UserDeviceTokenRepository tokenRepository;

    private DeviceTokenService service;

    @BeforeEach
    void setUp() {
        service = new DeviceTokenService(tokenRepository, CLOCK);
    }

    @Test
    void registerCreatesTokenForAuthenticatedUser() {
        UUID userId = UUID.randomUUID();
        when(tokenRepository.findByDeviceId("device-1")).thenReturn(Optional.empty());
        when(tokenRepository.findByFcmToken("fcm-token-aaa")).thenReturn(Optional.empty());
        when(tokenRepository.save(any())).thenAnswer(invocation -> {
            UserDeviceTokenEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        var response = service.register(userId, request("fcm-token-aaa", "device-1"));

        assertThat(response.getDeviceId()).isEqualTo("device-1");
        assertThat(response.isActive()).isTrue();
        ArgumentCaptor<UserDeviceTokenEntity> captor = ArgumentCaptor.forClass(UserDeviceTokenEntity.class);
        verify(tokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getFcmToken()).isEqualTo("fcm-token-aaa");
        assertThat(captor.getValue().getLastSeenAt()).isEqualTo(LocalDateTime.now(CLOCK));
    }

    @Test
    void registerSameTokenDoesNotCreateDuplicate() {
        UUID userId = UUID.randomUUID();
        UserDeviceTokenEntity existing = token(userId, "device-1", "fcm-token-aaa");
        when(tokenRepository.findByDeviceId("device-1")).thenReturn(Optional.of(existing));
        when(tokenRepository.findByFcmToken("fcm-token-aaa")).thenReturn(Optional.of(existing));
        when(tokenRepository.save(existing)).thenReturn(existing);

        service.register(userId, request("fcm-token-aaa", "device-1"));

        verify(tokenRepository, never()).delete(any());
        verify(tokenRepository).save(existing);
        assertThat(existing.isActive()).isTrue();
    }

    @Test
    void registerTransfersDeviceToNewUser() {
        UUID previousUser = UUID.randomUUID();
        UUID nextUser = UUID.randomUUID();
        UserDeviceTokenEntity existing = token(previousUser, "device-1", "old-token");
        when(tokenRepository.findByDeviceId("device-1")).thenReturn(Optional.of(existing));
        when(tokenRepository.findByFcmToken("new-token")).thenReturn(Optional.empty());
        when(tokenRepository.save(existing)).thenReturn(existing);

        service.register(nextUser, request("new-token", "device-1"));

        assertThat(existing.getUserId()).isEqualTo(nextUser);
        assertThat(existing.getFcmToken()).isEqualTo("new-token");
        assertThat(existing.isActive()).isTrue();
    }

    @Test
    void logoutDeactivatesOnlyCurrentDevice() {
        UUID userId = UUID.randomUUID();
        UserDeviceTokenEntity current = token(userId, "device-1", "token-a");
        when(tokenRepository.findByUserIdAndDeviceId(userId, "device-1")).thenReturn(Optional.of(current));
        when(tokenRepository.save(current)).thenReturn(current);

        service.deactivateCurrentDevice(userId, "device-1");

        assertThat(current.isActive()).isFalse();
        verify(tokenRepository).save(current);
    }

    @Test
    void logoutDoesNotTouchAnotherUsersDevice() {
        UUID userId = UUID.randomUUID();
        when(tokenRepository.findByUserIdAndDeviceId(userId, "device-1")).thenReturn(Optional.empty());

        service.deactivateCurrentDevice(userId, "device-1");

        verify(tokenRepository, never()).save(any());
    }

    @Test
    void multipleDevicesRemainActiveForSameUser() {
        UUID userId = UUID.randomUUID();
        UserDeviceTokenEntity phone = token(userId, "phone", "token-phone");
        UserDeviceTokenEntity tablet = token(userId, "tablet", "token-tablet");
        when(tokenRepository.findByUserIdAndActiveTrue(userId)).thenReturn(List.of(phone, tablet));

        assertThat(service.listActiveForUser(userId)).hasSize(2);
    }

    @Test
    void deactivateInvalidTokens() {
        UserDeviceTokenEntity entity = token(UUID.randomUUID(), "device-1", "dead-token");
        when(tokenRepository.findByFcmTokenIn(List.of("dead-token"))).thenReturn(List.of(entity));

        service.deactivateTokens(List.of("dead-token"));

        assertThat(entity.isActive()).isFalse();
        verify(tokenRepository).saveAll(List.of(entity));
    }

    private static RegisterDeviceTokenRequest request(String token, String deviceId) {
        RegisterDeviceTokenRequest request = new RegisterDeviceTokenRequest();
        request.setToken(token);
        request.setDeviceId(deviceId);
        request.setPlatform(DevicePlatform.ANDROID);
        request.setAppVersion("1.0");
        return request;
    }

    private static UserDeviceTokenEntity token(UUID userId, String deviceId, String fcmToken) {
        UserDeviceTokenEntity entity = UserDeviceTokenEntity.builder()
                .userId(userId)
                .deviceId(deviceId)
                .fcmToken(fcmToken)
                .platform(DevicePlatform.ANDROID)
                .active(true)
                .lastSeenAt(LocalDateTime.now(CLOCK))
                .build();
        entity.setId(UUID.randomUUID());
        return entity;
    }
}
