package com.acomi.acomi_backend.notification.application.service;

import com.acomi.acomi_backend.notification.api.dto.request.RegisterDeviceTokenRequest;
import com.acomi.acomi_backend.notification.api.dto.response.DeviceTokenResponse;
import com.acomi.acomi_backend.notification.application.support.TokenMask;
import com.acomi.acomi_backend.notification.infrastructure.persistence.entity.UserDeviceTokenEntity;
import com.acomi.acomi_backend.notification.infrastructure.persistence.repository.UserDeviceTokenRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final UserDeviceTokenRepository tokenRepository;
    private final Clock clock;

    @Transactional
    public DeviceTokenResponse register(UUID userId, RegisterDeviceTokenRequest request) {
        String token = request.getToken().trim();
        String deviceId = request.getDeviceId().trim();
        LocalDateTime now = LocalDateTime.now(clock);

        Optional<UserDeviceTokenEntity> byDevice = tokenRepository.findByDeviceId(deviceId);
        Optional<UserDeviceTokenEntity> byToken = tokenRepository.findByFcmToken(token);

        UserDeviceTokenEntity entity;
        if (byDevice.isPresent()) {
            entity = byDevice.get();
            if (byToken.isPresent() && !byToken.get().getId().equals(entity.getId())) {
                tokenRepository.delete(byToken.get());
                tokenRepository.flush();
            }
            applyOwnership(entity, userId, token, request, now);
        } else if (byToken.isPresent()) {
            entity = byToken.get();
            entity.setDeviceId(deviceId);
            applyOwnership(entity, userId, token, request, now);
        } else {
            entity = UserDeviceTokenEntity.builder()
                    .userId(userId)
                    .fcmToken(token)
                    .platform(request.getPlatform())
                    .deviceId(deviceId)
                    .appVersion(blankToNull(request.getAppVersion()))
                    .active(true)
                    .lastSeenAt(now)
                    .build();
        }

        UserDeviceTokenEntity saved = tokenRepository.save(entity);
        log.info(
                "device token registered userId={} deviceId={} platform={} token={}",
                userId,
                deviceId,
                request.getPlatform(),
                TokenMask.mask(token));
        return DeviceTokenResponse.from(saved);
    }

    @Transactional
    public void deactivateCurrentDevice(UUID userId, String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return;
        }
        tokenRepository.findByUserIdAndDeviceId(userId, deviceId.trim()).ifPresent(entity -> {
            entity.setActive(false);
            tokenRepository.save(entity);
            log.info(
                    "device token deactivated userId={} deviceId={} token={}",
                    userId,
                    entity.getDeviceId(),
                    TokenMask.mask(entity.getFcmToken()));
        });
    }

    @Transactional
    public void deactivateTokens(Collection<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        List<UserDeviceTokenEntity> rows = tokenRepository.findByFcmTokenIn(tokens);
        if (rows.isEmpty()) {
            return;
        }
        for (UserDeviceTokenEntity row : rows) {
            row.setActive(false);
            log.info(
                    "invalid FCM token deactivated userId={} token={}",
                    row.getUserId(),
                    TokenMask.mask(row.getFcmToken()));
        }
        tokenRepository.saveAll(rows);
    }

    @Transactional(readOnly = true)
    public List<UserDeviceTokenEntity> listActiveForUser(UUID userId) {
        return tokenRepository.findByUserIdAndActiveTrue(userId);
    }

    private static void applyOwnership(
            UserDeviceTokenEntity entity,
            UUID userId,
            String token,
            RegisterDeviceTokenRequest request,
            LocalDateTime now) {
        entity.setUserId(userId);
        entity.setFcmToken(token);
        entity.setPlatform(request.getPlatform());
        entity.setAppVersion(blankToNull(request.getAppVersion()));
        entity.setActive(true);
        entity.setLastSeenAt(now);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
