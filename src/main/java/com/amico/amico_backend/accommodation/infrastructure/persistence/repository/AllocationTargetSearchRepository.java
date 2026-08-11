package com.amico.amico_backend.accommodation.infrastructure.persistence.repository;

import com.amico.amico_backend.accommodation.domain.model.AccommodationStatus;
import com.amico.amico_backend.accommodation.infrastructure.persistence.projection.AllocationTargetSearchRow;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AllocationTargetSearchRepository extends JpaRepository<com.amico.amico_backend.accommodation.infrastructure.persistence.entity.BedEntity, UUID> {

    @Query("""
            SELECT new com.amico.amico_backend.accommodation.infrastructure.persistence.projection.AllocationTargetSearchRow(
                com.amico.amico_backend.occupancy.domain.model.AllocationTargetType.BED,
                b.id,
                COALESCE(bf.id, bu.id),
                COALESCE(bf.name, bu.name),
                COALESCE(bf.code, bu.code),
                f.id,
                f.name,
                ru.id,
                ru.name,
                ru.unitNumber,
                r.id,
                r.name,
                r.roomNumber,
                b.id,
                b.name,
                b.bedNumber,
                b.status,
                b.defaultRent,
                b.defaultDeposit
            )
            FROM BedEntity b
            JOIN b.room r
            LEFT JOIN r.floor f
            LEFT JOIN f.building bf
            LEFT JOIN r.unit ru
            LEFT JOIN ru.building bu
            WHERE b.isActive = true
              AND r.isActive = true
              AND (
                  (f IS NOT NULL AND f.isActive = true AND bf.isActive = true AND bf.space.id = :spaceId)
                  OR (ru IS NOT NULL AND ru.isActive = true AND bu.isActive = true AND bu.space.id = :spaceId)
              )
              AND (:buildingId IS NULL OR COALESCE(bf.id, bu.id) = :buildingId)
              AND (:floorId IS NULL OR f.id = :floorId)
              AND (:unitId IS NULL OR ru.id = :unitId)
              AND (:status IS NULL OR b.status = :status)
              AND (:selectableOnly = false OR b.status = com.amico.amico_backend.accommodation.domain.model.AccommodationStatus.AVAILABLE)
              AND (
                  :query IS NULL OR :query = ''
                  OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(b.bedNumber) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(r.name) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(r.roomNumber) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(f.name) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(CAST(f.floorNumber AS string)) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(ru.name) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(ru.unitNumber) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(COALESCE(bf.name, bu.name)) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(COALESCE(bf.code, bu.code)) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            ORDER BY COALESCE(bf.name, bu.name), f.sortOrder, f.floorNumber, ru.unitNumber, r.roomNumber, b.bedNumber
            """)
    Page<AllocationTargetSearchRow> searchBedTargets(
            @Param("spaceId") UUID spaceId,
            @Param("query") String query,
            @Param("buildingId") UUID buildingId,
            @Param("floorId") UUID floorId,
            @Param("unitId") UUID unitId,
            @Param("status") AccommodationStatus status,
            @Param("selectableOnly") boolean selectableOnly,
            Pageable pageable);

    @Query("""
            SELECT new com.amico.amico_backend.accommodation.infrastructure.persistence.projection.AllocationTargetSearchRow(
                com.amico.amico_backend.occupancy.domain.model.AllocationTargetType.UNIT,
                u.id,
                b.id,
                b.name,
                b.code,
                NULL,
                NULL,
                u.id,
                u.name,
                u.unitNumber,
                NULL,
                NULL,
                NULL,
                NULL,
                NULL,
                NULL,
                u.status,
                u.defaultRent,
                u.defaultDeposit
            )
            FROM UnitEntity u
            JOIN u.building b
            WHERE u.isActive = true
              AND b.isActive = true
              AND b.space.id = :spaceId
              AND u.synthetic = false
              AND (:buildingId IS NULL OR b.id = :buildingId)
              AND (:unitId IS NULL OR u.id = :unitId)
              AND (:status IS NULL OR u.status = :status)
              AND (:selectableOnly = false OR u.status = com.amico.amico_backend.accommodation.domain.model.AccommodationStatus.AVAILABLE)
              AND (
                  :query IS NULL OR :query = ''
                  OR LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(u.unitNumber) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))
                  OR LOWER(b.code) LIKE LOWER(CONCAT('%', :query, '%'))
              )
            ORDER BY b.name, u.unitNumber
            """)
    Page<AllocationTargetSearchRow> searchUnitTargets(
            @Param("spaceId") UUID spaceId,
            @Param("query") String query,
            @Param("buildingId") UUID buildingId,
            @Param("unitId") UUID unitId,
            @Param("status") AccommodationStatus status,
            @Param("selectableOnly") boolean selectableOnly,
            Pageable pageable);
}
