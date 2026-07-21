package com.countin.countin_backend.meal.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "space_food_item_settings")
@IdClass(SpaceFoodItemSettingsEntity.Pk.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpaceFoodItemSettingsEntity {

    @Id
    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Id
    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Builder.Default
    @Column(name = "is_enabled", nullable = false)
    private boolean isEnabled = true;

    /** Mess-only: item is available to enable as a meal add-on from Menu Library. */
    @Builder.Default
    @Column(name = "is_extra", nullable = false)
    private boolean isExtra = false;

    @Column(name = "default_price", precision = 10, scale = 2)
    private java.math.BigDecimal defaultPrice;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private UUID spaceId;
        private UUID itemId;
    }
}
