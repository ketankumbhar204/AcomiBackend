package com.countin.countin_backend.occupancy.infrastructure.persistence.repository;

import com.countin.countin_backend.occupancy.infrastructure.persistence.entity.OccupancyAmenityEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OccupancyAmenityRepository extends JpaRepository<OccupancyAmenityEntity, UUID> {

    List<OccupancyAmenityEntity> findAllByOccupancyIdOrderByDisplayOrderAscCreatedAtAsc(UUID occupancyId);

    void deleteByOccupancyId(UUID occupancyId);
}
