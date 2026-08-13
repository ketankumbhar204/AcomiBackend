package com.acomi.acomi_backend.inventory.infrastructure.persistence.repository;

import com.acomi.acomi_backend.inventory.infrastructure.persistence.entity.InventoryCategoryEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryCategoryRepository extends JpaRepository<InventoryCategoryEntity, UUID> {

    List<InventoryCategoryEntity> findBySpaceIdAndIsActiveTrueOrderBySortOrderAscNameAsc(UUID spaceId);

    boolean existsBySpaceId(UUID spaceId);

    Optional<InventoryCategoryEntity> findByIdAndSpaceIdAndIsActiveTrue(UUID id, UUID spaceId);

    @Query(
            """
            select count(c) > 0 from InventoryCategoryEntity c
            where c.space.id = :spaceId and c.isDefault = true and c.isActive = true
            """)
    boolean existsDefaultForSpace(@Param("spaceId") UUID spaceId);
}
