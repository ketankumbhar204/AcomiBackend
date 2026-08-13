package com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository;

import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyHistoryEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OccupancyHistoryRepository extends JpaRepository<OccupancyHistoryEntity, UUID> {

    @Query("""
            SELECT h FROM OccupancyHistoryEntity h
            WHERE h.space.id = :spaceId AND h.member.id = :memberId
            ORDER BY h.performedAt DESC
            """)
    List<OccupancyHistoryEntity> findBySpaceIdAndMemberIdOrderByPerformedAtDesc(
            @Param("spaceId") UUID spaceId, @Param("memberId") UUID memberId);

    List<OccupancyHistoryEntity> findByOccupancyIdOrderByPerformedAtDesc(UUID occupancyId);
}
