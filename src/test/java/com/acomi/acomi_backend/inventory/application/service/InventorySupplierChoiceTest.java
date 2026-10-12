package com.acomi.acomi_backend.inventory.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.inventory.api.dto.request.UpdateInventoryItemRequest;
import com.acomi.acomi_backend.inventory.domain.model.InventoryUnit;
import com.acomi.acomi_backend.inventory.infrastructure.persistence.entity.InventoryCategoryEntity;
import com.acomi.acomi_backend.inventory.infrastructure.persistence.entity.InventoryItemEntity;
import com.acomi.acomi_backend.inventory.infrastructure.persistence.entity.InventorySupplierEntity;
import com.acomi.acomi_backend.inventory.infrastructure.persistence.repository.InventoryCategoryRepository;
import com.acomi.acomi_backend.inventory.infrastructure.persistence.repository.InventoryItemRepository;
import com.acomi.acomi_backend.inventory.infrastructure.persistence.repository.InventorySupplierRepository;
import com.acomi.acomi_backend.inventory.infrastructure.persistence.repository.InventoryTransactionRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventorySupplierChoiceTest {

    @Mock
    private InventoryAccessService accessService;
    @Mock
    private InventorySeedService seedService;
    @Mock
    private SpaceRepository spaceRepository;
    @Mock
    private InventoryCategoryRepository categoryRepository;
    @Mock
    private InventoryItemRepository itemRepository;
    @Mock
    private InventorySupplierRepository supplierRepository;
    @Mock
    private InventoryTransactionRepository transactionRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private UUID spaceId;
    private UUID itemId;
    private UUID callerId;
    private SpaceEntity space;
    private InventoryItemEntity item;
    private InventorySupplierEntity seeded;

    @BeforeEach
    void setUp() {
        spaceId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        callerId = UUID.randomUUID();
        space = SpaceEntity.builder().name("Sunrise PG").type(SpaceType.PG).build();
        space.setId(spaceId);
        InventoryCategoryEntity category = InventoryCategoryEntity.builder()
                .space(space)
                .name("Bedding")
                .code("BEDDING")
                .build();
        category.setId(UUID.randomUUID());
        seeded = InventorySupplierEntity.builder()
                .space(space)
                .name("General Supplies")
                .isActive(true)
                .build();
        seeded.setId(UUID.randomUUID());
        item = InventoryItemEntity.builder()
                .space(space)
                .category(category)
                .name("Bedsheet")
                .unit(InventoryUnit.PIECE)
                .supplier(seeded)
                .build();
        item.setId(itemId);
        when(itemRepository.findActiveByIdAndSpaceId(itemId, spaceId)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(InventoryItemEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void updateLeavesTheSavedSupplierWhenTheRequestOmitsIt() {
        UpdateInventoryItemRequest request = new UpdateInventoryItemRequest();
        request.setName("Bedsheet");

        var response = inventoryService.updateItem(spaceId, itemId, callerId, request);

        assertThat(response.getSupplierId()).isEqualTo(seeded.getId());
        assertThat(response.getSupplierName()).isEqualTo("General Supplies");
        verify(supplierRepository, never()).save(any());
    }

    @Test
    void updateReusesAnExistingSupplierByName() {
        when(supplierRepository.findBySpaceIdAndIsActiveTrueAndNameIgnoreCase(spaceId, "general supplies"))
                .thenReturn(Optional.of(seeded));
        UpdateInventoryItemRequest request = new UpdateInventoryItemRequest();
        request.setSupplierName("  general supplies  ");

        var response = inventoryService.updateItem(spaceId, itemId, callerId, request);

        assertThat(response.getSupplierId()).isEqualTo(seeded.getId());
        assertThat(response.getSupplierName()).isEqualTo("General Supplies");
        verify(supplierRepository, never()).save(any());
    }

    @Test
    void updateCreatesASupplierForANewName() {
        UUID createdId = UUID.randomUUID();
        when(supplierRepository.findBySpaceIdAndIsActiveTrueAndNameIgnoreCase(spaceId, "Linen House"))
                .thenReturn(Optional.empty());
        when(supplierRepository.save(any(InventorySupplierEntity.class))).thenAnswer(invocation -> {
            InventorySupplierEntity supplier = invocation.getArgument(0);
            supplier.setId(createdId);
            return supplier;
        });
        UpdateInventoryItemRequest request = new UpdateInventoryItemRequest();
        request.setSupplierName("Linen House");

        var response = inventoryService.updateItem(spaceId, itemId, callerId, request);

        ArgumentCaptor<InventorySupplierEntity> captor = ArgumentCaptor.forClass(InventorySupplierEntity.class);
        verify(supplierRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Linen House");
        assertThat(captor.getValue().getSpace()).isSameAs(space);
        assertThat(response.getSupplierId()).isEqualTo(createdId);
        assertThat(response.getSupplierName()).isEqualTo("Linen House");
    }

    @Test
    void updateSelectsAnExistingSupplierById() {
        UUID otherId = UUID.randomUUID();
        InventorySupplierEntity other = InventorySupplierEntity.builder()
                .space(space)
                .name("City Linens")
                .isActive(true)
                .build();
        other.setId(otherId);
        when(supplierRepository.findByIdAndSpaceIdAndIsActiveTrue(otherId, spaceId)).thenReturn(Optional.of(other));
        UpdateInventoryItemRequest request = new UpdateInventoryItemRequest();
        request.setSupplierId(otherId);

        var response = inventoryService.updateItem(spaceId, itemId, callerId, request);

        assertThat(response.getSupplierId()).isEqualTo(otherId);
        assertThat(response.getSupplierName()).isEqualTo("City Linens");
        verify(supplierRepository, never()).save(any());
    }

    @Test
    void updateClearsTheSupplierWhenTheNameIsBlank() {
        UpdateInventoryItemRequest request = new UpdateInventoryItemRequest();
        request.setSupplierName("  ");

        var response = inventoryService.updateItem(spaceId, itemId, callerId, request);

        assertThat(response.getSupplierId()).isNull();
        assertThat(response.getSupplierName()).isNull();
        assertThat(item.getSupplier()).isNull();
    }
}
