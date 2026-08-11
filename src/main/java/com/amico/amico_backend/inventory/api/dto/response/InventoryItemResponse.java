package com.amico.amico_backend.inventory.api.dto.response;

import com.amico.amico_backend.inventory.domain.model.InventoryUnit;
import com.amico.amico_backend.inventory.infrastructure.persistence.entity.InventoryItemEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InventoryItemResponse {

    private UUID itemId;
    private UUID spaceId;
    private String name;
    private UUID categoryId;
    private InventoryUnit unit;
    private BigDecimal currentStock;
    private BigDecimal reservedStock;
    private BigDecimal minimumStock;
    private String location;
    private UUID supplierId;
    private BigDecimal purchasePrice;
    private BigDecimal averagePrice;
    private String barcode;
    private String notes;
    private String statusOverride;
    private String imageUri;
    private LocalDateTime expiresAt;
    private LocalDateTime warrantyUntil;
    private String assignedEntityType;
    private UUID assignedEntityId;

    @JsonProperty("isDefault")
    private boolean isDefault;

    @JsonProperty("isActive")
    private boolean active;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static InventoryItemResponse from(InventoryItemEntity entity) {
        return InventoryItemResponse.builder()
                .itemId(entity.getId())
                .spaceId(entity.getSpace().getId())
                .name(entity.getName())
                .categoryId(entity.getCategory().getId())
                .unit(entity.getUnit())
                .currentStock(entity.getCurrentStock())
                .reservedStock(entity.getReservedStock())
                .minimumStock(entity.getMinimumStock())
                .location(entity.getLocation())
                .supplierId(entity.getSupplier() != null ? entity.getSupplier().getId() : null)
                .purchasePrice(entity.getPurchasePrice())
                .averagePrice(entity.getAveragePrice())
                .barcode(entity.getBarcode())
                .notes(entity.getNotes())
                .statusOverride(entity.getStatusOverride())
                .imageUri(null)
                .expiresAt(entity.getExpiresAt())
                .warrantyUntil(entity.getWarrantyUntil())
                .assignedEntityType(entity.getAssignedEntityType())
                .assignedEntityId(entity.getAssignedEntityId())
                .isDefault(entity.isDefault())
                .active(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
