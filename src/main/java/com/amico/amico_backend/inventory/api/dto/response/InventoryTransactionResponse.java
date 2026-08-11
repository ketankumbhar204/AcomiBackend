package com.amico.amico_backend.inventory.api.dto.response;

import com.amico.amico_backend.inventory.domain.model.InventoryTxnType;
import com.amico.amico_backend.inventory.domain.model.InventoryUnit;
import com.amico.amico_backend.inventory.infrastructure.persistence.entity.InventoryTransactionEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InventoryTransactionResponse {

    private UUID transactionId;
    private UUID spaceId;
    private UUID itemId;
    private String itemName;
    private InventoryTxnType type;
    private BigDecimal quantity;
    private InventoryUnit unit;
    private String reason;
    private String reference;
    private UUID supplierId;
    private String supplierName;
    private BigDecimal amount;
    private String actorName;
    private LocalDateTime createdAt;

    public static InventoryTransactionResponse from(InventoryTransactionEntity entity) {
        return InventoryTransactionResponse.builder()
                .transactionId(entity.getId())
                .spaceId(entity.getSpace().getId())
                .itemId(entity.getItem().getId())
                .itemName(entity.getItemName())
                .type(entity.getType())
                .quantity(entity.getQuantity())
                .unit(entity.getUnit())
                .reason(entity.getReason())
                .reference(entity.getReference())
                .supplierId(entity.getSupplier() != null ? entity.getSupplier().getId() : null)
                .supplierName(entity.getSupplierName())
                .amount(entity.getAmount())
                .actorName(entity.getActorName())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
