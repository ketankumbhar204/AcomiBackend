package com.amico.amico_backend.accommodation.infrastructure.persistence.repository;

import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BuildingRepository extends JpaRepository<BuildingEntity, UUID> {

    @Query("""
            SELECT b FROM BuildingEntity b
            WHERE b.space.id = :spaceId
              AND b.isActive = true
            ORDER BY b.name ASC
            """)
    List<BuildingEntity> findActiveBySpaceId(@Param("spaceId") UUID spaceId);

    @Query("""
            SELECT b FROM BuildingEntity b
            WHERE b.id = :id
              AND b.space.id = :spaceId
              AND b.isActive = true
            """)
    Optional<BuildingEntity> findActiveByIdAndSpaceId(
            @Param("id") UUID id, @Param("spaceId") UUID spaceId);

    boolean existsBySpaceIdAndNameAndIsActiveTrue(UUID spaceId, String name);

    @Query("""
            SELECT b FROM BuildingEntity b
            WHERE b.id = :id
              AND b.space.id = :spaceId
            """)
    Optional<BuildingEntity> findByIdAndSpaceId(
            @Param("id") UUID id, @Param("spaceId") UUID spaceId);
}
