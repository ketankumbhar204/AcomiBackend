package com.acomi.acomi_backend.inventory.api.dto.request;

import com.acomi.acomi_backend.inventory.domain.model.InventoryUnit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateInventoryItemRequest {

    @Size(max = 150)
    private String name;

    private UUID categoryId;

    private InventoryUnit unit;

    @DecimalMin("0")
    private BigDecimal minimumStock;

    @Size(max = 150)
    private String location;

    private UUID supplierId;

    @DecimalMin("0")
    private BigDecimal purchasePrice;

    @DecimalMin("0")
    private BigDecimal averagePrice;

    @Size(max = 80)
    private String barcode;

    private String notes;

    @Size(max = 30)
    private String statusOverride;
}
