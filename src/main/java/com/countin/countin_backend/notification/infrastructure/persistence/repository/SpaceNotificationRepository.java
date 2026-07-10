package com.countin.countin_backend.notification.infrastructure.persistence.repository;

import com.countin.countin_backend.notification.domain.model.NotificationCategory;
import com.countin.countin_backend.notification.domain.model.NotificationEntityType;
import com.countin.countin_backend.notification.domain.model.NotificationStatus;
import com.countin.countin_backend.notification.domain.model.NotificationType;
import com.countin.countin_backend.notification.infrastructure.persistence.entity.SpaceNotificationEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SpaceNotificationRepository extends JpaRepository<SpaceNotificationEntity, UUID> {

    Optional<SpaceNotificationEntity> findBySpaceIdAndDedupeKeyAndStatusIn(
            UUID spaceId, String dedupeKey, Collection<NotificationStatus> statuses);

    List<SpaceNotificationEntity> findBySpaceIdAndUserIdAndStatusInOrderByCreatedAtDesc(
            UUID spaceId, UUID userId, Collection<NotificationStatus> statuses);

    @Query("""
            SELECT n FROM SpaceNotificationEntity n
            WHERE n.spaceId = :spaceId
              AND n.userId = :userId
              AND n.category = :category
              AND n.status IN :statuses
            ORDER BY n.priority DESC, n.createdAt DESC
            """)
    List<SpaceNotificationEntity> findActionable(
            @Param("spaceId") UUID spaceId,
            @Param("userId") UUID userId,
            @Param("category") NotificationCategory category,
            @Param("statuses") Collection<NotificationStatus> statuses);

    List<SpaceNotificationEntity> findBySpaceIdAndEntityTypeAndEntityIdAndStatusIn(
            UUID spaceId,
            NotificationEntityType entityType,
            UUID entityId,
            Collection<NotificationStatus> statuses);

    List<SpaceNotificationEntity> findBySpaceIdAndNotificationTypeAndStatusIn(
            UUID spaceId, NotificationType notificationType, Collection<NotificationStatus> statuses);

    long countBySpaceIdAndUserIdAndCategoryAndStatusIn(
            UUID spaceId,
            UUID userId,
            NotificationCategory category,
            Collection<NotificationStatus> statuses);

    @Query("""
            SELECT n FROM SpaceNotificationEntity n
            WHERE n.userId = :userId
              AND n.spaceId IN :spaceIds
              AND n.category = :category
              AND n.status IN :statuses
            ORDER BY n.priority DESC, n.createdAt DESC
            """)
    List<SpaceNotificationEntity> findActionableByUserAndSpaces(
            @Param("userId") UUID userId,
            @Param("spaceIds") Collection<UUID> spaceIds,
            @Param("category") NotificationCategory category,
            @Param("statuses") Collection<NotificationStatus> statuses);

    @Query("""
            SELECT n FROM SpaceNotificationEntity n
            WHERE n.userId = :userId
              AND n.spaceId IN :spaceIds
              AND n.category IN :categories
              AND n.status IN :statuses
            ORDER BY n.createdAt DESC
            """)
    List<SpaceNotificationEntity> findRecentByUserAndSpaces(
            @Param("userId") UUID userId,
            @Param("spaceIds") Collection<UUID> spaceIds,
            @Param("categories") Collection<NotificationCategory> categories,
            @Param("statuses") Collection<NotificationStatus> statuses,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT COUNT(n) FROM SpaceNotificationEntity n
            WHERE n.userId = :userId
              AND n.spaceId IN :spaceIds
              AND n.status = :status
            """)
    long countByUserAndSpacesAndStatus(
            @Param("userId") UUID userId,
            @Param("spaceIds") Collection<UUID> spaceIds,
            @Param("status") NotificationStatus status);
}
