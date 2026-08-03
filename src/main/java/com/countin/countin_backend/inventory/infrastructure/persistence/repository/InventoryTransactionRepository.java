package com.countin.countin_backend.inventory.infrastructure.persistence.repository;

import com.countin.countin_backend.inventory.infrastructure.persistence.entity.InventoryTransactionEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryTransactionRepository
        extends JpaRepository<InventoryTransactionEntity, UUID> {

    @Query(
            """
            select t from InventoryTransactionEntity t
            left join fetch t.supplier
            where t.space.id = :spaceId
            order by t.createdAt desc
            """)
    List<InventoryTransactionEntity> findBySpaceIdOrderByCreatedAtDesc(@Param("spaceId") UUID spaceId);

    @Query(
            """
            select t from InventoryTransactionEntity t
            left join fetch t.supplier
            where t.space.id = :spaceId and t.item.id = :itemId
            order by t.createdAt desc
            """)
    List<InventoryTransactionEntity> findBySpaceIdAndItemIdOrderByCreatedAtDesc(
            @Param("spaceId") UUID spaceId, @Param("itemId") UUID itemId);
}
