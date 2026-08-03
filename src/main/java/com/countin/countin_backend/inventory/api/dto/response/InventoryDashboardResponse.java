package com.countin.countin_backend.inventory.api.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InventoryDashboardResponse {

    private long totalItems;
    private BigDecimal inventoryValue;
    private long lowStockCount;
    private long outOfStockCount;
    private long supplierCount;
    private List<InventoryTransactionResponse> recentPurchases;
    private List<InventoryTransactionResponse> recentConsumption;
    private List<InventoryItemResponse> criticalItems;
}
