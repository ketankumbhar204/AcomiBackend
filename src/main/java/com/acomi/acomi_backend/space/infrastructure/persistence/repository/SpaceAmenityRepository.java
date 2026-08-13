package com.acomi.acomi_backend.space.infrastructure.persistence.repository;

import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceAmenityEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpaceAmenityRepository extends JpaRepository<SpaceAmenityEntity, UUID> {

    List<SpaceAmenityEntity> findAllBySpaceIdOrderByDisplayOrderAscCreatedAtAsc(UUID spaceId);

    void deleteBySpaceId(UUID spaceId);
}
