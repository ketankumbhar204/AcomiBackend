package com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository;

import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.FloorEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FloorRepository extends JpaRepository<FloorEntity, UUID> {

    @Query("""
            SELECT f FROM FloorEntity f
            WHERE f.building.id = :buildingId
              AND f.isActive = true
            ORDER BY f.sortOrder ASC, f.floorNumber ASC
            """)
    List<FloorEntity> findActiveByBuildingId(@Param("buildingId") UUID buildingId);

    @Query("""
            SELECT f FROM FloorEntity f
            WHERE f.id = :id
              AND f.building.id = :buildingId
              AND f.isActive = true
            """)
    Optional<FloorEntity> findActiveByIdAndBuildingId(
            @Param("id") UUID id, @Param("buildingId") UUID buildingId);

    @Query("""
            SELECT f FROM FloorEntity f
            WHERE f.id = :id
              AND f.building.space.id = :spaceId
              AND f.isActive = true
            """)
    Optional<FloorEntity> findActiveByIdAndSpaceId(
            @Param("id") UUID id, @Param("spaceId") UUID spaceId);

    boolean existsByBuildingIdAndFloorNumberAndIsActiveTrue(UUID buildingId, int floorNumber);

    boolean existsByBuildingIdAndIsActiveTrue(UUID buildingId);

    @Query("SELECT COUNT(f) > 0 FROM FloorEntity f WHERE f.building.id = :buildingId")
    boolean existsByBuildingId(@Param("buildingId") UUID buildingId);

    @Query("SELECT f FROM FloorEntity f WHERE f.building.id = :buildingId")
    List<FloorEntity> findAllByBuildingId(@Param("buildingId") UUID buildingId);

    @Query("""
            SELECT f FROM FloorEntity f
            JOIN FETCH f.building
            WHERE f.id = :id
              AND f.building.space.id = :spaceId
            """)
    Optional<FloorEntity> findByIdAndSpaceId(
            @Param("id") UUID id, @Param("spaceId") UUID spaceId);
}
