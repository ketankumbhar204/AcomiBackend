package com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity;

import com.acomi.acomi_backend.accommodation.domain.model.AccommodationStatus;
import com.acomi.acomi_backend.accommodation.domain.model.UnitKind;
import com.acomi.acomi_backend.common.model.BaseEntity;
import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "units",
        indexes = {@Index(name = "idx_units_building_id", columnList = "building_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnitEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private BuildingEntity building;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id")
    private FloorEntity floor;

    @Column(nullable = false)
    private String name;

    @Column(name = "unit_number", nullable = false, length = 50)
    private String unitNumber;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccommodationStatus status = AccommodationStatus.AVAILABLE;

    @Builder.Default
    @Column(nullable = false)
    private boolean synthetic = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_kind", length = 30)
    private UnitKind unitKind;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "default_rent", precision = 12, scale = 2)
    private BigDecimal defaultRent;

    @Column(name = "default_deposit", precision = 12, scale = 2)
    private BigDecimal defaultDeposit;
}
