package com.acomi.acomi_backend.favorite.infrastructure.persistence.repository;

import com.acomi.acomi_backend.favorite.infrastructure.persistence.entity.UserSpaceFavoriteEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserSpaceFavoriteRepository extends JpaRepository<UserSpaceFavoriteEntity, UUID> {

    Optional<UserSpaceFavoriteEntity> findByUserIdAndSpaceId(UUID userId, UUID spaceId);

    List<UserSpaceFavoriteEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("""
            SELECT f.spaceId FROM UserSpaceFavoriteEntity f
            WHERE f.userId = :userId
            ORDER BY f.createdAt DESC
            """)
    List<UUID> findSpaceIdsByUserId(@Param("userId") UUID userId);

    void deleteByUserIdAndSpaceId(UUID userId, UUID spaceId);
}
