package com.acomi.acomi_backend.space.infrastructure.persistence.repository;

import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

@Repository
public interface SpaceRepository extends JpaRepository<SpaceEntity, UUID>, JpaSpecificationExecutor<SpaceEntity> {

    Optional<SpaceEntity> findByIdAndIsActiveTrue(UUID id);

    @Query("""
            SELECT s FROM SpaceEntity s
            JOIN FETCH s.owner
            WHERE s.id = :id
              AND s.isActive = true
            """)
    Optional<SpaceEntity> findActiveWithOwnerById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SpaceEntity s WHERE s.id = :id")
    Optional<SpaceEntity> lockById(@Param("id") UUID id);

    Optional<SpaceEntity> findByIdAndIsActiveTrueAndDiscoverableTrue(UUID id);

    List<SpaceEntity> findByIsActiveTrue();

    List<SpaceEntity> findAllByOwnerId(UUID ownerId);

    @Query("""
            SELECT DISTINCT s FROM SpaceEntity s
            JOIN SpaceMembershipEntity sm ON sm.space = s
            WHERE sm.user.id = :userId
              AND sm.status = com.acomi.acomi_backend.member.domain.model.MembershipStatus.ACTIVE
              AND s.isActive = true
            """)
    List<SpaceEntity> findAllActiveSpacesForUser(@Param("userId") UUID userId);

    List<SpaceEntity> findByOwnerIdAndIsActiveTrue(UUID ownerId);

    List<SpaceEntity> findByTypeAndIsActiveTrue(SpaceType type);

    boolean existsByIdAndOwnerIdAndIsActiveTrue(UUID id, UUID ownerId);

    boolean existsByOwnerIdAndIsActiveTrue(UUID ownerId);

    boolean existsByOwnerIdAndIsActiveTrueAndNameIgnoreCase(UUID ownerId, String name);

    boolean existsByOwnerIdAndIsActiveTrueAndNameIgnoreCaseAndIdNot(
            UUID ownerId, String name, UUID id);

    long countByTypeAndIsActiveTrue(SpaceType type);

    long countByTypeInAndIsActiveTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            List<SpaceType> types, java.time.LocalDateTime from, java.time.LocalDateTime to);

    @Query(
            """
            SELECT s FROM SpaceEntity s
            WHERE s.isActive = true
              AND s.type IN :types
            ORDER BY s.createdAt DESC
            """)
    List<SpaceEntity> findActiveByTypes(@Param("types") List<SpaceType> types);

    @Query(
            """
            SELECT s FROM SpaceEntity s
            WHERE s.isActive = true
              AND s.contactNumber IN :mobiles
            """)
    List<SpaceEntity> findActiveByContactNumberIn(@Param("mobiles") Collection<String> mobiles);

    @Query(
            """
            SELECT s FROM SpaceEntity s
            WHERE s.isActive = true
              AND s.latitude IS NOT NULL
              AND s.longitude IS NOT NULL
              AND s.latitude BETWEEN :minLat AND :maxLat
              AND s.longitude BETWEEN :minLng AND :maxLng
            """)
    List<SpaceEntity> findActiveInGeoBox(
            @Param("minLat") BigDecimal minLat,
            @Param("maxLat") BigDecimal maxLat,
            @Param("minLng") BigDecimal minLng,
            @Param("maxLng") BigDecimal maxLng);

    @Query(
            value =
                    """
                    SELECT CAST(s.created_at AS date) AS day, COUNT(*) AS cnt
                    FROM spaces s
                    WHERE s.is_active = true
                      AND s.type IN (:types)
                      AND s.created_at >= :fromAt
                      AND s.created_at < :toAt
                    GROUP BY CAST(s.created_at AS date)
                    ORDER BY day ASC
                    """,
            nativeQuery = true)
    List<Object[]> countDailyActiveCreatedByTypesBetween(
            @Param("types") Collection<String> types,
            @Param("fromAt") java.time.LocalDateTime fromAt,
            @Param("toAt") java.time.LocalDateTime toAt);
}
