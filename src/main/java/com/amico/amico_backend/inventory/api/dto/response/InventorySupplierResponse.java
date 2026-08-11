package com.amico.amico_backend.inventory.api.dto.response;

import com.amico.amico_backend.inventory.infrastructure.persistence.entity.InventorySupplierEntity;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InventorySupplierResponse {

    private UUID supplierId;
    private UUID spaceId;
    private String name;
    private String phone;
    private String address;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static InventorySupplierResponse from(InventorySupplierEntity entity) {
        return InventorySupplierResponse.builder()
                .supplierId(entity.getId())
                .spaceId(entity.getSpace().getId())
                .name(entity.getName())
                .phone(entity.getPhone())
                .address(entity.getAddress())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
