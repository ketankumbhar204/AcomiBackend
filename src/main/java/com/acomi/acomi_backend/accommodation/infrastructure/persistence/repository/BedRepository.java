package com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository;

import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BedRepository extends JpaRepository<BedEntity, UUID> {

    @Query("""
            SELECT b FROM BedEntity b
            WHERE b.room.id = :roomId
              AND b.isActive = true
            ORDER BY b.bedNumber ASC
            """)
    List<BedEntity> findActiveByRoomId(@Param("roomId") UUID roomId);

    @Query("""
            SELECT b FROM BedEntity b
            WHERE b.id = :id
              AND b.room.id = :roomId
              AND b.isActive = true
            """)
    Optional<BedEntity> findActiveByIdAndRoomId(
            @Param("id") UUID id, @Param("roomId") UUID roomId);

    boolean existsByRoomIdAndBedNumberAndIsActiveTrue(UUID roomId, String bedNumber);

    boolean existsByRoomIdAndIsActiveTrue(UUID roomId);

    long countByRoomIdAndIsActiveTrue(UUID roomId);

    @Query("""
            SELECT b.bedNumber FROM BedEntity b
            WHERE b.room.id = :roomId AND b.isActive = true
            """)
    List<String> findActiveBedNumbersByRoomId(@Param("roomId") UUID roomId);

    boolean existsByRoomId(UUID roomId);

    @Query("SELECT b FROM BedEntity b WHERE b.room.id = :roomId")
    List<BedEntity> findAllByRoomId(@Param("roomId") UUID roomId);

    @Query("""
            SELECT COUNT(b) > 0 FROM BedEntity b
            JOIN b.room r
            WHERE r.floor.id = :floorId
            """)
    boolean existsByFloorId(@Param("floorId") UUID floorId);

    @Query("SELECT b FROM BedEntity b JOIN b.room r WHERE r.floor.id = :floorId")
    List<BedEntity> findAllByFloorId(@Param("floorId") UUID floorId);

    @Query("""
            SELECT b FROM BedEntity b
            JOIN b.room r
            WHERE r.floor.id = :floorId
               OR r.unit.floor.id = :floorId
            """)
    List<BedEntity> findAllByFloorIdIncludingUnits(@Param("floorId") UUID floorId);

    @Query("""
            SELECT COUNT(b) > 0 FROM BedEntity b
            JOIN b.room r
            WHERE r.unit.id = :unitId
            """)
    boolean existsByUnitId(@Param("unitId") UUID unitId);

    @Query("SELECT b FROM BedEntity b JOIN b.room r WHERE r.unit.id = :unitId")
    List<BedEntity> findAllByUnitId(@Param("unitId") UUID unitId);

    @Query("""
            SELECT COUNT(b) > 0 FROM BedEntity b
            JOIN b.room r
            WHERE (r.floor IS NOT NULL AND r.floor.building.id = :buildingId)
               OR (r.unit IS NOT NULL AND r.unit.building.id = :buildingId)
            """)
    boolean existsByBuildingId(@Param("buildingId") UUID buildingId);

    @Query("""
            SELECT b FROM BedEntity b
            JOIN b.room r
            WHERE (r.floor IS NOT NULL AND r.floor.building.id = :buildingId)
               OR (r.unit IS NOT NULL AND r.unit.building.id = :buildingId)
            """)
    List<BedEntity> findAllByBuildingId(@Param("buildingId") UUID buildingId);

    @Query("""
            SELECT b FROM BedEntity b
            JOIN FETCH b.room r
            LEFT JOIN FETCH r.floor f
            LEFT JOIN FETCH f.building
            LEFT JOIN FETCH r.unit u
            LEFT JOIN FETCH u.building
            WHERE b.id = :id
              AND (
                  (r.floor IS NOT NULL AND r.floor.building.space.id = :spaceId)
                  OR (r.unit IS NOT NULL AND r.unit.building.space.id = :spaceId)
              )
            """)
    Optional<BedEntity> findByIdAndSpaceId(
            @Param("id") UUID id, @Param("spaceId") UUID spaceId);

    @Query("""
            SELECT DISTINCT b FROM BedEntity b
            JOIN FETCH b.room r
            LEFT JOIN FETCH r.floor f
            LEFT JOIN FETCH f.building
            LEFT JOIN FETCH r.unit u
            LEFT JOIN FETCH u.building
            WHERE b.isActive = true
              AND (
                  (r.floor IS NOT NULL AND r.floor.building.id = :buildingId)
                  OR (r.unit IS NOT NULL AND r.unit.building.id = :buildingId)
              )
            """)
    List<BedEntity> findActiveFetchedByBuildingId(@Param("buildingId") UUID buildingId);
}
