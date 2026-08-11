package com.amico.amico_backend.inventory.api.dto.request;

import com.amico.amico_backend.inventory.domain.model.InventoryTxnType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InventoryStockMoveRequest {

    @NotNull
    private InventoryTxnType type;

    @NotNull
    @DecimalMin("0")
    private BigDecimal quantity;

    @Size(max = 255)
    private String reason;

    @Size(max = 100)
    private String reference;

    private UUID supplierId;

    @DecimalMin("0")
    private BigDecimal amount;

    /** For ADJUSTMENT: set absolute stock instead of delta when provided. */
    @DecimalMin("0")
    private BigDecimal setAbsoluteStock;

    @Size(max = 100)
    private String actorName;
}
