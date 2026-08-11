package com.amico.amico_backend.inventory.application.service;

import com.amico.amico_backend.inventory.application.catalog.InventoryProfiles;
import com.amico.amico_backend.inventory.application.catalog.InventoryProfiles.Profile;
import com.amico.amico_backend.inventory.application.catalog.InventoryProfiles.SeedCategory;
import com.amico.amico_backend.inventory.application.catalog.InventoryProfiles.SeedItem;
import com.amico.amico_backend.inventory.domain.model.InventoryTxnType;
import com.amico.amico_backend.inventory.infrastructure.persistence.entity.InventoryCategoryEntity;
import com.amico.amico_backend.inventory.infrastructure.persistence.entity.InventoryItemEntity;
import com.amico.amico_backend.inventory.infrastructure.persistence.entity.InventorySupplierEntity;
import com.amico.amico_backend.inventory.infrastructure.persistence.entity.InventoryTransactionEntity;
import com.amico.amico_backend.inventory.infrastructure.persistence.repository.InventoryCategoryRepository;
import com.amico.amico_backend.inventory.infrastructure.persistence.repository.InventoryItemRepository;
import com.amico.amico_backend.inventory.infrastructure.persistence.repository.InventorySupplierRepository;
import com.amico.amico_backend.inventory.infrastructure.persistence.repository.InventoryTransactionRepository;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds default inventory categories and items exactly once per space,
 * mirroring meal library space-create / lazy-GET setup.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventorySeedService {

    private final InventoryCategoryRepository categoryRepository;
    private final InventoryItemRepository itemRepository;
    private final InventorySupplierRepository supplierRepository;
    private final InventoryTransactionRepository transactionRepository;

    @Transactional
    public void seedDefaults(SpaceEntity space) {
        if (space == null || !space.isActive()) {
            return;
        }
        if (categoryRepository.existsBySpaceId(space.getId())) {
            return;
        }

        Profile profile = InventoryProfiles.forSpaceType(space.getType());
        log.info(
                "Seeding inventory defaults for space {} ({}) using profile {}",
                space.getId(),
                space.getType(),
                profile.name());

        Map<String, InventoryCategoryEntity> byCode = new HashMap<>();
        int sort = 0;
        for (SeedCategory seed : profile.categories()) {
            InventoryCategoryEntity category = categoryRepository.save(InventoryCategoryEntity.builder()
                    .space(space)
                    .name(seed.name())
                    .code(seed.code())
                    .iconKey(seed.iconKey())
                    .sortOrder(sort++)
                    .isDefault(true)
                    .isActive(true)
                    .build());
            byCode.put(seed.code(), category);
        }

        InventorySupplierEntity supplier = null;
        if (profile.supportsSupplier()) {
            supplier = supplierRepository.save(InventorySupplierEntity.builder()
                    .space(space)
                    .name(profile.defaultSupplierName())
                    .phone(null)
                    .address(null)
                    .notes("Default seeded supplier")
                    .isActive(true)
                    .build());
        }

        for (SeedItem seed : profile.items()) {
            InventoryCategoryEntity category = byCode.get(seed.categoryCode());
            if (category == null) {
                log.warn(
                        "Skipping seed item '{}' — unknown category code {}",
                        seed.name(),
                        seed.categoryCode());
                continue;
            }
            InventoryItemEntity item = itemRepository.save(InventoryItemEntity.builder()
                    .space(space)
                    .category(category)
                    .name(seed.name())
                    .unit(seed.unit())
                    .currentStock(seed.currentStock())
                    .minimumStock(seed.minimumStock())
                    .reservedStock(java.math.BigDecimal.ZERO)
                    .purchasePrice(seed.purchasePrice())
                    .averagePrice(seed.purchasePrice())
                    .supplier(supplier)
                    .location(seed.location())
                    .isDefault(true)
                    .isActive(true)
                    .build());

            if (seed.currentStock().signum() > 0) {
                transactionRepository.save(InventoryTransactionEntity.builder()
                        .space(space)
                        .item(item)
                        .itemName(item.getName())
                        .type(InventoryTxnType.STOCK_IN)
                        .quantity(seed.currentStock())
                        .unit(seed.unit())
                        .reason("Opening stock")
                        .supplier(supplier)
                        .supplierName(supplier != null ? supplier.getName() : null)
                        .amount(null)
                        .actorName("System")
                        .build());
            }
        }
    }
}
