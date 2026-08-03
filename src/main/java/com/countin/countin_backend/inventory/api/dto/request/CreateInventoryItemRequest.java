package com.countin.countin_backend.inventory.api.dto.request;

import com.countin.countin_backend.inventory.domain.model.InventoryUnit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateInventoryItemRequest {

    @NotBlank
    @Size(max = 150)
    private String name;

    @NotNull
    private UUID categoryId;

    @NotNull
    private InventoryUnit unit;

    @NotNull
    @DecimalMin("0")
    private BigDecimal openingStock;

    @NotNull
    @DecimalMin("0")
    private BigDecimal minimumStock;

    @Size(max = 150)
    private String location;

    private UUID supplierId;

    @DecimalMin("0")
    private BigDecimal purchasePrice;

    @Size(max = 80)
    private String barcode;

    private String notes;
}
