package com.amico.amico_backend.occupancy.infrastructure.persistence.repository;

import com.amico.amico_backend.occupancy.infrastructure.persistence.entity.OccupancyChargeSnapshotEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OccupancyChargeSnapshotRepository extends JpaRepository<OccupancyChargeSnapshotEntity, UUID> {

    List<OccupancyChargeSnapshotEntity> findAllByOccupancyIdOrderByCreatedAtAsc(UUID occupancyId);
}
