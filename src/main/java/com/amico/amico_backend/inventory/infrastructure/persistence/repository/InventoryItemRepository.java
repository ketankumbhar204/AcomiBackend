package com.amico.amico_backend.inventory.infrastructure.persistence.repository;

import com.amico.amico_backend.inventory.infrastructure.persistence.entity.InventoryItemEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryItemRepository extends JpaRepository<InventoryItemEntity, UUID> {

    @Query(
            """
            select i from InventoryItemEntity i
            join fetch i.category
            left join fetch i.supplier
            where i.space.id = :spaceId and i.isActive = true
            order by i.name asc
            """)
    List<InventoryItemEntity> findActiveBySpaceId(@Param("spaceId") UUID spaceId);

    @Query(
            """
            select i from InventoryItemEntity i
            join fetch i.category
            left join fetch i.supplier
            where i.space.id = :spaceId and i.category.id = :categoryId and i.isActive = true
            order by i.name asc
            """)
    List<InventoryItemEntity> findActiveBySpaceIdAndCategoryId(
            @Param("spaceId") UUID spaceId, @Param("categoryId") UUID categoryId);

    @Query(
            """
            select i from InventoryItemEntity i
            join fetch i.category
            left join fetch i.supplier
            where i.id = :id and i.space.id = :spaceId and i.isActive = true
            """)
    Optional<InventoryItemEntity> findActiveByIdAndSpaceId(
            @Param("id") UUID id, @Param("spaceId") UUID spaceId);

    boolean existsByCategoryIdAndIsActiveTrue(UUID categoryId);

    long countBySpaceIdAndIsActiveTrue(UUID spaceId);
}
