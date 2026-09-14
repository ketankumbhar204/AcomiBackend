package com.acomi.acomi_backend.notification.infrastructure.persistence.entity;

import com.acomi.acomi_backend.common.model.BaseEntity;
import com.acomi.acomi_backend.notification.domain.model.DevicePlatform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "user_device_tokens",
        indexes = {
            @Index(name = "idx_user_device_tokens_user_active", columnList = "user_id, is_active")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDeviceTokenEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "fcm_token", nullable = false, columnDefinition = "TEXT")
    private String fcmToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private DevicePlatform platform;

    @Column(name = "device_id", nullable = false, length = 128)
    private String deviceId;

    @Column(name = "app_version", length = 40)
    private String appVersion;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;
}
