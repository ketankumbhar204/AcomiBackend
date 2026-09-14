package com.acomi.acomi_backend.notification.infrastructure.persistence.repository;

import com.acomi.acomi_backend.notification.infrastructure.persistence.entity.UserDeviceTokenEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserDeviceTokenRepository extends JpaRepository<UserDeviceTokenEntity, UUID> {

    Optional<UserDeviceTokenEntity> findByFcmToken(String fcmToken);

    Optional<UserDeviceTokenEntity> findByDeviceId(String deviceId);

    Optional<UserDeviceTokenEntity> findByUserIdAndDeviceId(UUID userId, String deviceId);

    List<UserDeviceTokenEntity> findByUserIdAndActiveTrue(UUID userId);

    List<UserDeviceTokenEntity> findByUserIdInAndActiveTrue(Collection<UUID> userIds);

    List<UserDeviceTokenEntity> findByFcmTokenIn(Collection<String> fcmTokens);
}
