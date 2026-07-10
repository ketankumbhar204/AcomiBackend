package com.countin.countin_backend.occupancy.infrastructure.persistence.repository;

import com.countin.countin_backend.occupancy.domain.model.AllocationTargetType;
import com.countin.countin_backend.occupancy.domain.model.OccupancyStatus;
import com.countin.countin_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OccupancyRepository extends JpaRepository<OccupancyEntity, UUID> {

    @Query("""
            SELECT o FROM OccupancyEntity o
            JOIN FETCH o.member
            JOIN FETCH o.building
            LEFT JOIN FETCH o.floor
            LEFT JOIN FETCH o.unit
            LEFT JOIN FETCH o.room
            LEFT JOIN FETCH o.bed
            WHERE o.id = :id AND o.space.id = :spaceId
            """)
    Optional<OccupancyEntity> findByIdAndSpaceId(@Param("id") UUID id, @Param("spaceId") UUID spaceId);

    @Query("""
            SELECT o FROM OccupancyEntity o
            JOIN FETCH o.building
            LEFT JOIN FETCH o.floor
            LEFT JOIN FETCH o.unit
            LEFT JOIN FETCH o.room
            LEFT JOIN FETCH o.bed
            WHERE o.space.id = :spaceId
              AND o.member.id = :memberId
              AND o.status = com.countin.countin_backend.occupancy.domain.model.OccupancyStatus.ACTIVE
            """)
    Optional<OccupancyEntity> findActiveBySpaceIdAndMemberId(
            @Param("spaceId") UUID spaceId, @Param("memberId") UUID memberId);

    @Query("""
            SELECT o FROM OccupancyEntity o
            JOIN FETCH o.member
            JOIN FETCH o.building
            LEFT JOIN FETCH o.floor
            LEFT JOIN FETCH o.unit
            LEFT JOIN FETCH o.room
            LEFT JOIN FETCH o.bed
            WHERE o.space.id = :spaceId
              AND o.member.id = :memberId
              AND o.status = com.countin.countin_backend.occupancy.domain.model.OccupancyStatus.RESERVED
            """)
    Optional<OccupancyEntity> findReservedBySpaceIdAndMemberId(
            @Param("spaceId") UUID spaceId, @Param("memberId") UUID memberId);

    @Query("""
            SELECT o FROM OccupancyEntity o
            JOIN FETCH o.member
            WHERE o.bed.id = :bedId
              AND o.status IN (
                  com.countin.countin_backend.occupancy.domain.model.OccupancyStatus.ACTIVE,
                  com.countin.countin_backend.occupancy.domain.model.OccupancyStatus.RESERVED)
            """)
    Optional<OccupancyEntity> findCurrentByBedId(@Param("bedId") UUID bedId);

    boolean existsBySpaceIdAndMemberIdAndStatus(UUID spaceId, UUID memberId, OccupancyStatus status);

    boolean existsByBedIdAndStatus(UUID bedId, OccupancyStatus status);

    boolean existsByRoomIdAndStatus(UUID roomId, OccupancyStatus status);

    boolean existsByUnitIdAndStatus(UUID unitId, OccupancyStatus status);

    @Query("SELECT COUNT(o) > 0 FROM OccupancyEntity o WHERE o.space.id = :spaceId")
    boolean existsBySpaceId(@Param("spaceId") UUID spaceId);

    @Query("SELECT COUNT(o) > 0 FROM OccupancyEntity o WHERE o.bed.id = :bedId")
    boolean existsByBedId(@Param("bedId") UUID bedId);

    @Query("SELECT COUNT(o) > 0 FROM OccupancyEntity o WHERE o.room.id = :roomId")
    boolean existsByRoomId(@Param("roomId") UUID roomId);

    @Query("""
            SELECT COUNT(o) > 0 FROM OccupancyEntity o
            WHERE o.room.floor.id = :floorId OR o.unit.floor.id = :floorId
            """)
    boolean existsByFloorId(@Param("floorId") UUID floorId);

    @Query("SELECT COUNT(o) > 0 FROM OccupancyEntity o WHERE o.unit.id = :unitId")
    boolean existsByUnitId(@Param("unitId") UUID unitId);

    @Query("""
            SELECT COUNT(o) > 0 FROM OccupancyEntity o
            WHERE o.building.id = :buildingId
            """)
    boolean existsByBuildingId(@Param("buildingId") UUID buildingId);

    @Query("""
            SELECT o FROM OccupancyEntity o
            WHERE o.space.id = :spaceId
              AND o.member.id = :memberId
            ORDER BY o.allocatedAt DESC
            """)
    List<OccupancyEntity> findAllBySpaceIdAndMemberIdOrderByAllocatedAtDesc(
            @Param("spaceId") UUID spaceId, @Param("memberId") UUID memberId);

    @Query("""
            SELECT o FROM OccupancyEntity o
            WHERE o.space.id = :spaceId
              AND (:status IS NULL OR o.status = :status)
              AND (:memberId IS NULL OR o.member.id = :memberId)
              AND (:buildingId IS NULL OR o.building.id = :buildingId)
              AND (:floorId IS NULL OR o.floor.id = :floorId)
              AND (:unitId IS NULL OR o.unit.id = :unitId)
              AND (:roomId IS NULL OR o.room.id = :roomId)
              AND (:bedId IS NULL OR o.bed.id = :bedId)
              AND (:targetType IS NULL OR o.targetType = :targetType)
            ORDER BY o.allocatedAt DESC
            """)
    Page<OccupancyEntity> search(
            @Param("spaceId") UUID spaceId,
            @Param("status") OccupancyStatus status,
            @Param("memberId") UUID memberId,
            @Param("buildingId") UUID buildingId,
            @Param("floorId") UUID floorId,
            @Param("unitId") UUID unitId,
            @Param("roomId") UUID roomId,
            @Param("bedId") UUID bedId,
            @Param("targetType") AllocationTargetType targetType,
            Pageable pageable);

    @Query("""
            SELECT o FROM OccupancyEntity o
            JOIN FETCH o.member
            WHERE o.space.id = :spaceId
              AND o.status IN (
                  com.countin.countin_backend.occupancy.domain.model.OccupancyStatus.ACTIVE,
                  com.countin.countin_backend.occupancy.domain.model.OccupancyStatus.VACATED)
              AND (
                  (o.actualMoveInAt IS NOT NULL
                      AND o.actualMoveInAt < :monthEndExclusive)
                  OR (o.actualMoveInAt IS NULL AND o.moveInDate <= :monthEnd)
              )
              AND (o.vacatedAt IS NULL OR o.vacatedAt >= :monthStartTime)
            """)
    List<OccupancyEntity> findBillableBySpaceIdForMonth(
            @Param("spaceId") UUID spaceId,
            @Param("monthStartTime") LocalDateTime monthStartTime,
            @Param("monthEndExclusive") LocalDateTime monthEndExclusive,
            @Param("monthEnd") LocalDate monthEnd);

    @Query("""
            SELECT o FROM OccupancyEntity o
            JOIN FETCH o.member
            WHERE o.space.id = :spaceId
              AND o.status = com.countin.countin_backend.occupancy.domain.model.OccupancyStatus.ACTIVE
            """)
    List<OccupancyEntity> findActiveBySpaceId(@Param("spaceId") UUID spaceId);

    @Query("""
            SELECT COUNT(o) FROM OccupancyEntity o
            WHERE o.space.id = :spaceId
              AND o.status IN (
                  com.countin.countin_backend.occupancy.domain.model.OccupancyStatus.ACTIVE,
                  com.countin.countin_backend.occupancy.domain.model.OccupancyStatus.VACATED)
              AND (
                  (o.actualMoveInAt IS NOT NULL
                      AND o.actualMoveInAt >= :monthStartTime
                      AND o.actualMoveInAt < :monthEndExclusive)
                  OR (o.actualMoveInAt IS NULL
                      AND o.moveInDate >= :startDate
                      AND o.moveInDate <= :endDate)
              )
            """)
    long countMoveInsBetween(
            @Param("spaceId") UUID spaceId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("monthStartTime") LocalDateTime monthStartTime,
            @Param("monthEndExclusive") LocalDateTime monthEndExclusive);
}
