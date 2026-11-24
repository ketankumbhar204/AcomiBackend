package com.acomi.acomi_backend.space.infrastructure.persistence.repository;

import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceRuleEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpaceRuleRepository extends JpaRepository<SpaceRuleEntity, UUID> {

    List<SpaceRuleEntity> findAllBySpaceIdOrderByDisplayOrderAscCreatedAtAsc(UUID spaceId);

    @Query(
            """
            SELECT sr FROM SpaceRuleEntity sr
            WHERE sr.space.id IN :spaceIds
            ORDER BY sr.space.id ASC, sr.displayOrder ASC, sr.createdAt ASC
            """)
    List<SpaceRuleEntity> findAllBySpaceIdInOrderByDisplayOrderAscCreatedAtAsc(
            @Param("spaceIds") Collection<UUID> spaceIds);

    void deleteBySpaceId(UUID spaceId);
}
