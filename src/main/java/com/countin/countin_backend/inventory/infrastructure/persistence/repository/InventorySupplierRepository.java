package com.countin.countin_backend.inventory.infrastructure.persistence.repository;

import com.countin.countin_backend.inventory.infrastructure.persistence.entity.InventorySupplierEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventorySupplierRepository extends JpaRepository<InventorySupplierEntity, UUID> {

    List<InventorySupplierEntity> findBySpaceIdAndIsActiveTrueOrderByNameAsc(UUID spaceId);

    Optional<InventorySupplierEntity> findByIdAndSpaceIdAndIsActiveTrue(UUID id, UUID spaceId);

    long countBySpaceIdAndIsActiveTrue(UUID spaceId);
}
