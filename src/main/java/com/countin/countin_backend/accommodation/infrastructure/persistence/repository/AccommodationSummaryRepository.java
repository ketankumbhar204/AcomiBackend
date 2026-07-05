package com.countin.countin_backend.accommodation.infrastructure.persistence.repository;

import com.countin.countin_backend.accommodation.domain.model.AccommodationStatus;
import com.countin.countin_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AccommodationSummaryRepository extends JpaRepository<BuildingEntity, UUID> {

    @Query("""
            SELECT COUNT(f) FROM FloorEntity f
            WHERE f.building.id = :buildingId AND f.isActive = true
            """)
    long countActiveFloors(@Param("buildingId") UUID buildingId);

    @Query("""
            SELECT COUNT(u) FROM UnitEntity u
            WHERE u.building.id = :buildingId AND u.isActive = true
            """)
    long countActiveUnits(@Param("buildingId") UUID buildingId);

    @Query("""
            SELECT COUNT(u) FROM UnitEntity u
            WHERE u.building.id = :buildingId AND u.isActive = true AND u.synthetic = false
            """)
    long countVisibleActiveUnits(@Param("buildingId") UUID buildingId);

    @Query("""
            SELECT COUNT(u) FROM UnitEntity u
            WHERE u.building.id = :buildingId AND u.isActive = true AND u.synthetic = true
            """)
    long countSyntheticActiveUnits(@Param("buildingId") UUID buildingId);

    @Query("""
            SELECT COUNT(r) FROM RoomEntity r
            WHERE r.isActive = true
              AND EXISTS (
                  SELECT 1 FROM FloorEntity f
                  WHERE f.building.id = :buildingId AND f.isActive = true
                    AND (
                        r.floor.id = f.id
                        OR (r.unit IS NOT NULL AND r.unit.isActive = true AND r.unit.floor.id = f.id)
                    )
              )
            """)
    long countActiveRooms(@Param("buildingId") UUID buildingId);

    /**
     * Bed counts follow the same floor-scoped EXISTS pattern as {@link #countActiveRooms}.
     * Direct {@code room.unit.building} paths miss APARTMENT_PG beds (room → unit → floor → building).
     */
    @Query("""
            SELECT COUNT(b) FROM BedEntity b
            WHERE b.isActive = true
              AND b.room.isActive = true
              AND EXISTS (
                  SELECT 1 FROM FloorEntity f
                  WHERE f.building.id = :buildingId AND f.isActive = true
                    AND (
                        (b.room.floor IS NOT NULL AND b.room.floor.id = f.id)
                        OR (b.room.unit IS NOT NULL AND b.room.unit.isActive = true AND b.room.unit.floor.id = f.id)
                    )
              )
            """)
    long countActiveBeds(@Param("buildingId") UUID buildingId);

    @Query("""
            SELECT r.status, COUNT(r) FROM RoomEntity r
            WHERE r.isActive = true
              AND EXISTS (
                  SELECT 1 FROM FloorEntity f
                  WHERE f.building.id = :buildingId AND f.isActive = true
                    AND (
                        r.floor.id = f.id
                        OR (r.unit IS NOT NULL AND r.unit.isActive = true AND r.unit.floor.id = f.id)
                    )
              )
            GROUP BY r.status
            """)
    List<Object[]> countRoomStatuses(@Param("buildingId") UUID buildingId);

    @Query("""
            SELECT b.status, COUNT(b) FROM BedEntity b
            WHERE b.isActive = true
              AND b.room.isActive = true
              AND EXISTS (
                  SELECT 1 FROM FloorEntity f
                  WHERE f.building.id = :buildingId AND f.isActive = true
                    AND (
                        (b.room.floor IS NOT NULL AND b.room.floor.id = f.id)
                        OR (b.room.unit IS NOT NULL AND b.room.unit.isActive = true AND b.room.unit.floor.id = f.id)
                    )
              )
            GROUP BY b.status
            """)
    List<Object[]> countBedStatuses(@Param("buildingId") UUID buildingId);

    @Query("""
            SELECT u.status, COUNT(u) FROM UnitEntity u
            WHERE u.building.id = :buildingId AND u.isActive = true
            GROUP BY u.status
            """)
    List<Object[]> countUnitStatuses(@Param("buildingId") UUID buildingId);

    @Query("""
            SELECT COUNT(b) FROM BedEntity b
            WHERE b.isActive = true
              AND b.room.isActive = true
              AND b.status = :status
              AND EXISTS (
                  SELECT 1 FROM FloorEntity f
                  WHERE f.building.id = :buildingId AND f.isActive = true
                    AND (
                        (b.room.floor IS NOT NULL AND b.room.floor.id = f.id)
                        OR (b.room.unit IS NOT NULL AND b.room.unit.isActive = true AND b.room.unit.floor.id = f.id)
                    )
              )
            """)
    long countBedsByStatus(
            @Param("buildingId") UUID buildingId, @Param("status") AccommodationStatus status);

    @Query("""
            SELECT COUNT(b) FROM BedEntity b
            WHERE b.isActive = true
              AND b.room.isActive = true
              AND b.status = :status
              AND EXISTS (
                  SELECT 1 FROM FloorEntity f
                  WHERE f.building.space.id = :spaceId
                    AND f.isActive = true
                    AND f.building.isActive = true
                    AND (
                        (b.room.floor IS NOT NULL AND b.room.floor.id = f.id)
                        OR (b.room.unit IS NOT NULL AND b.room.unit.isActive = true AND b.room.unit.floor.id = f.id)
                    )
              )
            """)
    long countBedsByStatusForSpace(
            @Param("spaceId") UUID spaceId, @Param("status") AccommodationStatus status);

    @Query("""
            SELECT COUNT(r) FROM RoomEntity r
            WHERE r.isActive = true AND r.status = :status
              AND EXISTS (
                  SELECT 1 FROM FloorEntity f
                  WHERE f.building.id = :buildingId AND f.isActive = true
                    AND (
                        r.floor.id = f.id
                        OR (r.unit IS NOT NULL AND r.unit.isActive = true AND r.unit.floor.id = f.id)
                    )
              )
            """)
    long countRoomsByStatus(
            @Param("buildingId") UUID buildingId, @Param("status") AccommodationStatus status);

    @Query("""
            SELECT COUNT(u) FROM UnitEntity u
            WHERE u.building.id = :buildingId AND u.isActive = true AND u.status = :status
            """)
    long countUnitsByStatus(
            @Param("buildingId") UUID buildingId, @Param("status") AccommodationStatus status);
}
