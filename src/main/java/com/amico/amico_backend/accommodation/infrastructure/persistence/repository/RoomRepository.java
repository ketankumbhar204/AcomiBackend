package com.amico.amico_backend.accommodation.infrastructure.persistence.repository;

import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomRepository extends JpaRepository<RoomEntity, UUID> {

    @Query("""
            SELECT r FROM RoomEntity r
            JOIN FETCH r.floor f
            JOIN FETCH f.building
            WHERE r.floor.id = :floorId
              AND r.isActive = true
            ORDER BY r.roomNumber ASC
            """)
    List<RoomEntity> findActiveByFloorId(@Param("floorId") UUID floorId);

    @Query("""
            SELECT r FROM RoomEntity r
            LEFT JOIN FETCH r.unit u
            LEFT JOIN FETCH u.floor uf
            LEFT JOIN FETCH u.building
            WHERE r.isActive = true
              AND (
                  r.floor.id = :floorId
                  OR (u.floor.id = :floorId AND u.isActive = true)
              )
            ORDER BY r.roomNumber ASC
            """)
    List<RoomEntity> findActiveByFloorIdIncludingUnits(@Param("floorId") UUID floorId);

    @Query("""
            SELECT r FROM RoomEntity r
            JOIN FETCH r.unit u
            JOIN FETCH u.building
            WHERE r.unit.id = :unitId
              AND r.isActive = true
            ORDER BY r.roomNumber ASC
            """)
    List<RoomEntity> findActiveByUnitId(@Param("unitId") UUID unitId);

    @Query("""
            SELECT r FROM RoomEntity r
            WHERE r.id = :id
              AND r.isActive = true
            """)
    Optional<RoomEntity> findActiveById(@Param("id") UUID id);

    @Query("""
            SELECT r FROM RoomEntity r
            LEFT JOIN FETCH r.floor f
            LEFT JOIN FETCH f.building
            LEFT JOIN FETCH r.unit u
            LEFT JOIN FETCH u.building
            WHERE r.id = :id
              AND r.isActive = true
              AND (
                  (r.floor IS NOT NULL AND r.floor.building.space.id = :spaceId)
                  OR (r.unit IS NOT NULL AND r.unit.building.space.id = :spaceId)
              )
            """)
    Optional<RoomEntity> findActiveByIdAndSpaceId(
            @Param("id") UUID id, @Param("spaceId") UUID spaceId);

    boolean existsByFloorIdAndIsActiveTrue(UUID floorId);

    boolean existsByUnitIdAndIsActiveTrue(UUID unitId);

    boolean existsByFloorIdAndRoomNumberAndIsActiveTrue(UUID floorId, String roomNumber);

    boolean existsByUnitIdAndRoomNumberAndIsActiveTrue(UUID unitId, String roomNumber);

    @Query("""
            SELECT r.roomNumber FROM RoomEntity r
            WHERE r.isActive = true
              AND (
                  r.floor.id = :floorId
                  OR (r.unit.floor.id = :floorId AND r.unit.isActive = true)
              )
            """)
    List<String> findActiveRoomNumbersByFloorId(@Param("floorId") UUID floorId);

    @Query("""
            SELECT r.roomNumber FROM RoomEntity r
            WHERE r.unit.id = :unitId AND r.isActive = true
            """)
    List<String> findActiveRoomNumbersByUnitId(@Param("unitId") UUID unitId);

    boolean existsByFloorId(UUID floorId);

    @Query("SELECT r FROM RoomEntity r WHERE r.floor.id = :floorId")
    List<RoomEntity> findAllByFloorId(@Param("floorId") UUID floorId);

    @Query("""
            SELECT r FROM RoomEntity r
            WHERE r.floor.id = :floorId
               OR r.unit.floor.id = :floorId
            """)
    List<RoomEntity> findAllByFloorIdIncludingUnits(@Param("floorId") UUID floorId);

    boolean existsByUnitId(UUID unitId);

    @Query("SELECT r FROM RoomEntity r WHERE r.unit.id = :unitId")
    List<RoomEntity> findAllByUnitId(@Param("unitId") UUID unitId);

    @Query("""
            SELECT COUNT(r) > 0 FROM RoomEntity r
            WHERE r.isActive = true
              AND (
                  (r.floor IS NOT NULL AND r.floor.building.id = :buildingId)
                  OR (r.unit IS NOT NULL AND r.unit.building.id = :buildingId)
              )
            """)
    boolean existsByBuildingId(@Param("buildingId") UUID buildingId);

    @Query("""
            SELECT r FROM RoomEntity r
            WHERE (r.floor IS NOT NULL AND r.floor.building.id = :buildingId)
               OR (r.unit IS NOT NULL AND r.unit.building.id = :buildingId)
               OR (r.unit IS NOT NULL AND r.unit.floor IS NOT NULL AND r.unit.floor.building.id = :buildingId)
            """)
    List<RoomEntity> findAllByBuildingId(@Param("buildingId") UUID buildingId);

    @Query("""
            SELECT r FROM RoomEntity r
            LEFT JOIN FETCH r.floor f
            LEFT JOIN FETCH f.building
            LEFT JOIN FETCH r.unit u
            LEFT JOIN FETCH u.building
            WHERE r.id = :id
              AND (
                  (r.floor IS NOT NULL AND r.floor.building.space.id = :spaceId)
                  OR (r.unit IS NOT NULL AND r.unit.building.space.id = :spaceId)
              )
            """)
    Optional<RoomEntity> findByIdAndSpaceId(
            @Param("id") UUID id, @Param("spaceId") UUID spaceId);
}
