package com.countin.countin_backend.accommodation.infrastructure.persistence.repository;

import com.countin.countin_backend.accommodation.api.dto.response.BedListItemResponse;
import com.countin.countin_backend.accommodation.api.dto.response.BedSpaceListItemResponse;
import com.countin.countin_backend.accommodation.api.dto.response.FloorListItemResponse;
import com.countin.countin_backend.accommodation.api.dto.response.RoomListItemResponse;
import com.countin.countin_backend.accommodation.api.dto.response.UnitListItemResponse;
import com.countin.countin_backend.accommodation.domain.model.AccommodationStatus;
import com.countin.countin_backend.accommodation.infrastructure.persistence.entity.FloorEntity;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface AccommodationLazyListRepository extends Repository<FloorEntity, UUID> {

    @Query("""
            SELECT new com.countin.countin_backend.accommodation.api.dto.response.FloorListItemResponse(
                f.id,
                f.name,
                (SELECT COUNT(r) FROM RoomEntity r
                    WHERE r.isActive = true
                      AND (r.floor.id = f.id OR (r.unit.floor.id = f.id AND r.unit.isActive = true))),
                (SELECT COUNT(b) FROM BedEntity b
                    WHERE b.isActive = true AND b.room.isActive = true
                      AND (b.room.floor.id = f.id
                           OR (b.room.unit.floor.id = f.id AND b.room.unit.isActive = true))),
                (SELECT COUNT(b) FROM BedEntity b
                    WHERE b.isActive = true AND b.room.isActive = true
                      AND (b.room.floor.id = f.id
                           OR (b.room.unit.floor.id = f.id AND b.room.unit.isActive = true))
                      AND b.status = :availableStatus),
                (SELECT COUNT(b) FROM BedEntity b
                    WHERE b.isActive = true AND b.room.isActive = true
                      AND (b.room.floor.id = f.id
                           OR (b.room.unit.floor.id = f.id AND b.room.unit.isActive = true))
                      AND b.status = :occupiedStatus)
            )
            FROM FloorEntity f
            WHERE f.building.id = :buildingId AND f.isActive = true
              AND (:query IS NULL OR :query = '' OR LOWER(f.name) LIKE LOWER(CONCAT('%', :query, '%')))
            """)
    Page<FloorListItemResponse> findFloorListItemsByBuildingId(
            @Param("buildingId") UUID buildingId,
            @Param("query") String query,
            @Param("availableStatus") AccommodationStatus availableStatus,
            @Param("occupiedStatus") AccommodationStatus occupiedStatus,
            Pageable pageable);

    @Query("""
            SELECT new com.countin.countin_backend.accommodation.api.dto.response.FloorListItemResponse(
                f.id,
                f.name,
                (SELECT COUNT(r) FROM RoomEntity r
                    WHERE r.isActive = true
                      AND (r.floor.id = f.id OR (r.unit.floor.id = f.id AND r.unit.isActive = true))),
                (SELECT COUNT(b) FROM BedEntity b
                    WHERE b.isActive = true AND b.room.isActive = true
                      AND (b.room.floor.id = f.id
                           OR (b.room.unit.floor.id = f.id AND b.room.unit.isActive = true))),
                (SELECT COUNT(b) FROM BedEntity b
                    WHERE b.isActive = true AND b.room.isActive = true
                      AND (b.room.floor.id = f.id
                           OR (b.room.unit.floor.id = f.id AND b.room.unit.isActive = true))
                      AND b.status = :availableStatus),
                (SELECT COUNT(b) FROM BedEntity b
                    WHERE b.isActive = true AND b.room.isActive = true
                      AND (b.room.floor.id = f.id
                           OR (b.room.unit.floor.id = f.id AND b.room.unit.isActive = true))
                      AND b.status = :occupiedStatus)
            )
            FROM FloorEntity f
            WHERE f.building.space.id = :spaceId AND f.isActive = true AND f.building.isActive = true
              AND (:query IS NULL OR :query = '' OR LOWER(f.name) LIKE LOWER(CONCAT('%', :query, '%')))
            """)
    Page<FloorListItemResponse> findFloorListItemsBySpaceId(
            @Param("spaceId") UUID spaceId,
            @Param("query") String query,
            @Param("availableStatus") AccommodationStatus availableStatus,
            @Param("occupiedStatus") AccommodationStatus occupiedStatus,
            Pageable pageable);

    @Query("""
            SELECT new com.countin.countin_backend.accommodation.api.dto.response.UnitListItemResponse(
                u.id,
                u.name,
                (SELECT COUNT(r) FROM RoomEntity r WHERE r.unit.id = u.id AND r.isActive = true),
                (SELECT COUNT(b) FROM BedEntity b
                    WHERE b.room.unit.id = u.id AND b.isActive = true AND b.room.isActive = true),
                (SELECT COUNT(b) FROM BedEntity b
                    WHERE b.room.unit.id = u.id AND b.isActive = true AND b.room.isActive = true
                      AND b.status = :availableStatus),
                (SELECT COUNT(b) FROM BedEntity b
                    WHERE b.room.unit.id = u.id AND b.isActive = true AND b.room.isActive = true
                      AND b.status = :occupiedStatus),
                u.status,
                u.synthetic,
                u.unitKind
            )
            FROM UnitEntity u
            WHERE u.building.id = :buildingId AND u.isActive = true
              AND (:includeSynthetic = true OR u.synthetic = false)
              AND (:query IS NULL OR :query = '' OR LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%')))
            """)
    Page<UnitListItemResponse> findUnitListItemsByBuildingId(
            @Param("buildingId") UUID buildingId,
            @Param("query") String query,
            @Param("includeSynthetic") boolean includeSynthetic,
            @Param("availableStatus") AccommodationStatus availableStatus,
            @Param("occupiedStatus") AccommodationStatus occupiedStatus,
            Pageable pageable);

    @Query("""
            SELECT new com.countin.countin_backend.accommodation.api.dto.response.UnitListItemResponse(
                u.id,
                u.name,
                (SELECT COUNT(r) FROM RoomEntity r WHERE r.unit.id = u.id AND r.isActive = true),
                (SELECT COUNT(b) FROM BedEntity b
                    WHERE b.room.unit.id = u.id AND b.isActive = true AND b.room.isActive = true),
                (SELECT COUNT(b) FROM BedEntity b
                    WHERE b.room.unit.id = u.id AND b.isActive = true AND b.room.isActive = true
                      AND b.status = :availableStatus),
                (SELECT COUNT(b) FROM BedEntity b
                    WHERE b.room.unit.id = u.id AND b.isActive = true AND b.room.isActive = true
                      AND b.status = :occupiedStatus),
                u.status,
                u.synthetic,
                u.unitKind
            )
            FROM UnitEntity u
            WHERE u.building.space.id = :spaceId AND u.isActive = true AND u.building.isActive = true
              AND (:includeSynthetic = true OR u.synthetic = false)
              AND (:query IS NULL OR :query = '' OR LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%')))
            """)
    Page<UnitListItemResponse> findUnitListItemsBySpaceId(
            @Param("spaceId") UUID spaceId,
            @Param("query") String query,
            @Param("includeSynthetic") boolean includeSynthetic,
            @Param("availableStatus") AccommodationStatus availableStatus,
            @Param("occupiedStatus") AccommodationStatus occupiedStatus,
            Pageable pageable);

    @Query("""
            SELECT new com.countin.countin_backend.accommodation.api.dto.response.UnitListItemResponse(
                u.id,
                u.name,
                (SELECT COUNT(r) FROM RoomEntity r WHERE r.unit.id = u.id AND r.isActive = true),
                (SELECT COUNT(b) FROM BedEntity b
                    WHERE b.room.unit.id = u.id AND b.isActive = true AND b.room.isActive = true),
                (SELECT COUNT(b) FROM BedEntity b
                    WHERE b.room.unit.id = u.id AND b.isActive = true AND b.room.isActive = true
                      AND b.status = :availableStatus),
                (SELECT COUNT(b) FROM BedEntity b
                    WHERE b.room.unit.id = u.id AND b.isActive = true AND b.room.isActive = true
                      AND b.status = :occupiedStatus),
                u.status,
                u.synthetic,
                u.unitKind
            )
            FROM UnitEntity u
            WHERE u.floor.id = :floorId AND u.isActive = true
              AND (:includeSynthetic = true OR u.synthetic = false)
              AND (:query IS NULL OR :query = '' OR LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%')))
            """)
    Page<UnitListItemResponse> findUnitListItemsByFloorId(
            @Param("floorId") UUID floorId,
            @Param("query") String query,
            @Param("includeSynthetic") boolean includeSynthetic,
            @Param("availableStatus") AccommodationStatus availableStatus,
            @Param("occupiedStatus") AccommodationStatus occupiedStatus,
            Pageable pageable);

    @Query("""
            SELECT new com.countin.countin_backend.accommodation.api.dto.response.RoomListItemResponse(
                r.id,
                r.name,
                r.roomType,
                (SELECT COUNT(b) FROM BedEntity b WHERE b.room.id = r.id AND b.isActive = true),
                (SELECT COUNT(b) FROM BedEntity b
                    WHERE b.room.id = r.id AND b.isActive = true AND b.status = :availableStatus),
                (SELECT COUNT(b) FROM BedEntity b
                    WHERE b.room.id = r.id AND b.isActive = true AND b.status = :occupiedStatus)
            )
            FROM RoomEntity r
            WHERE r.isActive = true
              AND (
                  r.floor.id = :floorId
                  OR (r.unit.floor.id = :floorId AND r.unit.isActive = true AND r.unit.synthetic = true)
              )
              AND (:query IS NULL OR :query = '' OR LOWER(r.name) LIKE LOWER(CONCAT('%', :query, '%')))
            """)
    Page<RoomListItemResponse> findRoomListItemsByFloorId(
            @Param("floorId") UUID floorId,
            @Param("query") String query,
            @Param("availableStatus") AccommodationStatus availableStatus,
            @Param("occupiedStatus") AccommodationStatus occupiedStatus,
            Pageable pageable);

    @Query("""
            SELECT new com.countin.countin_backend.accommodation.api.dto.response.RoomListItemResponse(
                r.id,
                r.name,
                r.roomType,
                (SELECT COUNT(b) FROM BedEntity b WHERE b.room.id = r.id AND b.isActive = true),
                (SELECT COUNT(b) FROM BedEntity b
                    WHERE b.room.id = r.id AND b.isActive = true AND b.status = :availableStatus),
                (SELECT COUNT(b) FROM BedEntity b
                    WHERE b.room.id = r.id AND b.isActive = true AND b.status = :occupiedStatus)
            )
            FROM RoomEntity r
            WHERE r.unit.id = :unitId AND r.isActive = true
              AND (:query IS NULL OR :query = '' OR LOWER(r.name) LIKE LOWER(CONCAT('%', :query, '%')))
            """)
    Page<RoomListItemResponse> findRoomListItemsByUnitId(
            @Param("unitId") UUID unitId,
            @Param("query") String query,
            @Param("availableStatus") AccommodationStatus availableStatus,
            @Param("occupiedStatus") AccommodationStatus occupiedStatus,
            Pageable pageable);

    @Query("""
            SELECT new com.countin.countin_backend.accommodation.api.dto.response.RoomListItemResponse(
                r.id,
                r.name,
                r.roomType,
                (SELECT COUNT(b) FROM BedEntity b WHERE b.room.id = r.id AND b.isActive = true),
                (SELECT COUNT(b) FROM BedEntity b
                    WHERE b.room.id = r.id AND b.isActive = true AND b.status = :availableStatus),
                (SELECT COUNT(b) FROM BedEntity b
                    WHERE b.room.id = r.id AND b.isActive = true AND b.status = :occupiedStatus)
            )
            FROM RoomEntity r
            WHERE r.isActive = true
              AND (
                  (r.floor IS NOT NULL AND r.floor.building.space.id = :spaceId AND r.floor.isActive = true
                      AND r.floor.building.isActive = true)
                  OR (r.unit IS NOT NULL AND r.unit.building.space.id = :spaceId AND r.unit.isActive = true
                      AND r.unit.building.isActive = true)
              )
              AND (:query IS NULL OR :query = '' OR LOWER(r.name) LIKE LOWER(CONCAT('%', :query, '%')))
            """)
    Page<RoomListItemResponse> findRoomListItemsBySpaceId(
            @Param("spaceId") UUID spaceId,
            @Param("query") String query,
            @Param("availableStatus") AccommodationStatus availableStatus,
            @Param("occupiedStatus") AccommodationStatus occupiedStatus,
            Pageable pageable);

    @Query("""
            SELECT new com.countin.countin_backend.accommodation.api.dto.response.BedListItemResponse(
                b.id,
                b.bedNumber,
                b.status
            )
            FROM BedEntity b
            WHERE b.room.id = :roomId AND b.isActive = true
              AND (:query IS NULL OR :query = '' OR LOWER(b.bedNumber) LIKE LOWER(CONCAT('%', :query, '%')))
              AND (:status IS NULL OR b.status = :status)
            """)
    Page<BedListItemResponse> findBedListItemsByRoomId(
            @Param("roomId") UUID roomId,
            @Param("query") String query,
            @Param("status") AccommodationStatus status,
            Pageable pageable);

    @Query("""
            SELECT new com.countin.countin_backend.accommodation.api.dto.response.BedSpaceListItemResponse(
                b.id,
                b.bedNumber,
                b.status,
                COALESCE(bu.id, bf.id),
                COALESCE(bu.name, bf.name),
                COALESCE(uf.id, f.id),
                COALESCE(uf.name, f.name),
                ru.id,
                ru.name,
                r.id,
                r.name
            )
            FROM BedEntity b
            JOIN b.room r
            LEFT JOIN r.floor f
            LEFT JOIN f.building bf
            LEFT JOIN r.unit ru
            LEFT JOIN ru.building bu
            LEFT JOIN ru.floor uf
            WHERE b.isActive = true
              AND r.isActive = true
              AND (
                  (f IS NOT NULL AND f.isActive = true AND bf.isActive = true AND bf.space.id = :spaceId)
                  OR (ru IS NOT NULL AND ru.isActive = true AND bu.isActive = true AND bu.space.id = :spaceId)
              )
              AND (:status IS NULL OR b.status = :status)
              AND (:buildingId IS NULL OR COALESCE(bu.id, bf.id) = :buildingId)
              AND (:floorId IS NULL OR COALESCE(uf.id, f.id) = :floorId)
              AND (:unitId IS NULL OR ru.id = :unitId)
              AND (:query IS NULL OR :query = ''
                  OR LOWER(b.bedNumber) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(r.name) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(COALESCE(ru.name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(COALESCE(bu.name, bf.name, '')) LIKE LOWER(CONCAT('%', :query, '%')))
            """)
    Page<BedSpaceListItemResponse> findBedListItemsBySpaceId(
            @Param("spaceId") UUID spaceId,
            @Param("query") String query,
            @Param("status") AccommodationStatus status,
            @Param("buildingId") UUID buildingId,
            @Param("floorId") UUID floorId,
            @Param("unitId") UUID unitId,
            Pageable pageable);
}
