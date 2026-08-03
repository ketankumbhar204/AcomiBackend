package com.countin.countin_backend.inventory.infrastructure.persistence.entity;

import com.countin.countin_backend.common.model.BaseEntity;
import com.countin.countin_backend.inventory.domain.model.InventoryUnit;
import com.countin.countin_backend.space.infrastructure.persistence.entity.SpaceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "inventory_items",
        indexes = {
            @Index(name = "idx_inventory_items_space", columnList = "space_id, is_active"),
            @Index(name = "idx_inventory_items_category", columnList = "category_id"),
            @Index(name = "idx_inventory_items_supplier", columnList = "supplier_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItemEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "space_id", nullable = false)
    private SpaceEntity space;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private InventoryCategoryEntity category;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryUnit unit;

    @Builder.Default
    @Column(name = "minimum_stock", nullable = false, precision = 14, scale = 3)
    private BigDecimal minimumStock = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "current_stock", nullable = false, precision = 14, scale = 3)
    private BigDecimal currentStock = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "reserved_stock", nullable = false, precision = 14, scale = 3)
    private BigDecimal reservedStock = BigDecimal.ZERO;

    @Column(name = "purchase_price", precision = 14, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "average_price", precision = 14, scale = 2)
    private BigDecimal averagePrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private InventorySupplierEntity supplier;

    @Column(length = 150)
    private String location;

    @Column(length = 80)
    private String barcode;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "status_override", length = 30)
    private String statusOverride;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "warranty_until")
    private LocalDateTime warrantyUntil;

    @Column(name = "assigned_entity_type", length = 20)
    private String assignedEntityType;

    @Column(name = "assigned_entity_id")
    private UUID assignedEntityId;

    @Builder.Default
    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
