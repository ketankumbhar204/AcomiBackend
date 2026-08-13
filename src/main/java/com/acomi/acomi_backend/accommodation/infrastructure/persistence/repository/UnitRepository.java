package com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository;

import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.UnitEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UnitRepository extends JpaRepository<UnitEntity, UUID> {

    @Query("""
            SELECT u FROM UnitEntity u
            WHERE u.building.id = :buildingId
              AND u.isActive = true
              AND (:includeSynthetic = true OR u.synthetic = false)
            ORDER BY u.unitNumber ASC
            """)
    List<UnitEntity> findActiveByBuildingId(
            @Param("buildingId") UUID buildingId, @Param("includeSynthetic") boolean includeSynthetic);

    @Query("""
            SELECT u FROM UnitEntity u
            WHERE u.floor.id = :floorId
              AND u.isActive = true
              AND (:includeSynthetic = true OR u.synthetic = false)
            ORDER BY u.unitNumber ASC
            """)
    List<UnitEntity> findActiveByFloorId(
            @Param("floorId") UUID floorId, @Param("includeSynthetic") boolean includeSynthetic);

    boolean existsByFloorIdAndUnitNumberAndIsActiveTrue(UUID floorId, String unitNumber);

    @Query("""
            SELECT u FROM UnitEntity u
            WHERE u.id = :id
              AND u.building.id = :buildingId
              AND u.isActive = true
            """)
    Optional<UnitEntity> findActiveByIdAndBuildingId(
            @Param("id") UUID id, @Param("buildingId") UUID buildingId);

    @Query("""
            SELECT u FROM UnitEntity u
            WHERE u.id = :id
              AND u.building.space.id = :spaceId
              AND u.isActive = true
            """)
    Optional<UnitEntity> findActiveByIdAndSpaceId(
            @Param("id") UUID id, @Param("spaceId") UUID spaceId);

    boolean existsByBuildingIdAndUnitNumberAndIsActiveTrue(UUID buildingId, String unitNumber);

    @Query("""
            SELECT u.unitNumber FROM UnitEntity u
            WHERE u.building.id = :buildingId
              AND u.isActive = true
            """)
    List<String> findActiveUnitNumbersByBuildingId(@Param("buildingId") UUID buildingId);

    boolean existsByBuildingIdAndIsActiveTrue(UUID buildingId);

    @Query("SELECT COUNT(u) > 0 FROM UnitEntity u WHERE u.building.id = :buildingId")
    boolean existsByBuildingId(@Param("buildingId") UUID buildingId);

    @Query("SELECT u FROM UnitEntity u WHERE u.building.id = :buildingId")
    List<UnitEntity> findAllByBuildingId(@Param("buildingId") UUID buildingId);

    @Query("SELECT u FROM UnitEntity u WHERE u.floor.id = :floorId")
    List<UnitEntity> findAllByFloorId(@Param("floorId") UUID floorId);

    boolean existsByFloorIdAndIsActiveTrue(UUID floorId);

    @Query("""
            SELECT u FROM UnitEntity u
            JOIN FETCH u.building
            WHERE u.id = :id
              AND u.building.space.id = :spaceId
            """)
    Optional<UnitEntity> findByIdAndSpaceId(
            @Param("id") UUID id, @Param("spaceId") UUID spaceId);

    @Query("""
            SELECT COUNT(u) FROM UnitEntity u
            WHERE u.building.id = :buildingId AND u.isActive = true
            """)
    long countActiveByBuildingId(@Param("buildingId") UUID buildingId);

    @Query("""
            SELECT COUNT(u) FROM UnitEntity u
            WHERE u.building.id = :buildingId AND u.isActive = true AND u.synthetic = false
            """)
    long countVisibleActiveByBuildingId(@Param("buildingId") UUID buildingId);

    @Query("""
            SELECT COUNT(u) FROM UnitEntity u
            WHERE u.building.id = :buildingId AND u.isActive = true AND u.synthetic = true
            """)
    long countSyntheticActiveByBuildingId(@Param("buildingId") UUID buildingId);
}
