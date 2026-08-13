package com.acomi.acomi_backend.inventory.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.inventory.infrastructure.persistence.entity.InventoryCategoryEntity;
import com.acomi.acomi_backend.inventory.infrastructure.persistence.entity.InventoryItemEntity;
import com.acomi.acomi_backend.inventory.infrastructure.persistence.entity.InventorySupplierEntity;
import com.acomi.acomi_backend.inventory.infrastructure.persistence.entity.InventoryTransactionEntity;
import com.acomi.acomi_backend.inventory.infrastructure.persistence.repository.InventoryCategoryRepository;
import com.acomi.acomi_backend.inventory.infrastructure.persistence.repository.InventoryItemRepository;
import com.acomi.acomi_backend.inventory.infrastructure.persistence.repository.InventorySupplierRepository;
import com.acomi.acomi_backend.inventory.infrastructure.persistence.repository.InventoryTransactionRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventorySeedServiceTest {

    @Mock
    private InventoryCategoryRepository categoryRepository;

    @Mock
    private InventoryItemRepository itemRepository;

    @Mock
    private InventorySupplierRepository supplierRepository;

    @Mock
    private InventoryTransactionRepository transactionRepository;

    @InjectMocks
    private InventorySeedService inventorySeedService;

    private SpaceEntity space;

    @BeforeEach
    void setUp() {
        space = SpaceEntity.builder()
                .name("Annapurna Mess")
                .type(SpaceType.MESS)
                .isActive(true)
                .build();
        space.setId(UUID.randomUUID());
    }

    @Test
    void seedDefaults_createsCategoriesAndItems_whenEmpty() {
        when(categoryRepository.existsBySpaceId(space.getId())).thenReturn(false);
        when(categoryRepository.save(any(InventoryCategoryEntity.class))).thenAnswer(invocation -> {
            InventoryCategoryEntity category = invocation.getArgument(0);
            category.setId(UUID.randomUUID());
            return category;
        });
        when(supplierRepository.save(any(InventorySupplierEntity.class))).thenAnswer(invocation -> {
            InventorySupplierEntity supplier = invocation.getArgument(0);
            supplier.setId(UUID.randomUUID());
            return supplier;
        });
        when(itemRepository.save(any(InventoryItemEntity.class))).thenAnswer(invocation -> {
            InventoryItemEntity item = invocation.getArgument(0);
            item.setId(UUID.randomUUID());
            return item;
        });

        inventorySeedService.seedDefaults(space);

        ArgumentCaptor<InventoryCategoryEntity> categoryCaptor =
                ArgumentCaptor.forClass(InventoryCategoryEntity.class);
        verify(categoryRepository, org.mockito.Mockito.atLeast(5)).save(categoryCaptor.capture());
        assertThat(categoryCaptor.getAllValues())
                .extracting(InventoryCategoryEntity::getName)
                .contains("Grains", "Dairy", "Vegetables", "Oil", "Spices");
        verify(itemRepository, org.mockito.Mockito.atLeast(10)).save(any(InventoryItemEntity.class));
        verify(transactionRepository, never()).save(any(InventoryTransactionEntity.class));
    }

    @Test
    void seedDefaults_isIdempotent_whenCategoriesExist() {
        when(categoryRepository.existsBySpaceId(space.getId())).thenReturn(true);

        inventorySeedService.seedDefaults(space);

        verify(categoryRepository, never()).save(any());
        verify(itemRepository, never()).save(any());
    }
}
