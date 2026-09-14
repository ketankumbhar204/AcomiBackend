package com.acomi.acomi_backend.notification.api.dto.response;

import com.acomi.acomi_backend.notification.domain.model.DevicePlatform;
import com.acomi.acomi_backend.notification.infrastructure.persistence.entity.UserDeviceTokenEntity;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DeviceTokenResponse {
    UUID deviceTokenId;
    String deviceId;
    DevicePlatform platform;
    String appVersion;
    boolean active;
    LocalDateTime lastSeenAt;

    public static DeviceTokenResponse from(UserDeviceTokenEntity entity) {
        return DeviceTokenResponse.builder()
                .deviceTokenId(entity.getId())
                .deviceId(entity.getDeviceId())
                .platform(entity.getPlatform())
                .appVersion(entity.getAppVersion())
                .active(entity.isActive())
                .lastSeenAt(entity.getLastSeenAt())
                .build();
    }
}
