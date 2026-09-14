package com.acomi.acomi_backend.space.infrastructure.persistence.repository;

import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceAmenityEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpaceAmenityRepository extends JpaRepository<SpaceAmenityEntity, UUID> {

    List<SpaceAmenityEntity> findAllBySpaceIdOrderByDisplayOrderAscCreatedAtAsc(UUID spaceId);

    @Query(
            """
            SELECT sa FROM SpaceAmenityEntity sa
            WHERE sa.space.id IN :spaceIds
            ORDER BY sa.space.id ASC, sa.displayOrder ASC, sa.createdAt ASC
            """)
    List<SpaceAmenityEntity> findAllBySpaceIdInOrderByDisplayOrderAscCreatedAtAsc(
            @Param("spaceIds") Collection<UUID> spaceIds);

    void deleteBySpaceId(UUID spaceId);
}
