package com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity;

import com.acomi.acomi_backend.accommodation.domain.model.PropertyLayoutMode;
import com.acomi.acomi_backend.common.model.BaseEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
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
        name = "buildings",
        indexes = {
            @Index(name = "idx_buildings_space_id", columnList = "space_id"),
            @Index(name = "idx_buildings_space_active", columnList = "space_id, is_active")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BuildingEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "space_id", nullable = false)
    private SpaceEntity space;

    @Column(nullable = false)
    private String name;

    @Column(length = 50)
    private String code;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "layout_mode", nullable = false, length = 30)
    private PropertyLayoutMode layoutMode = PropertyLayoutMode.CORRIDOR_PG;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
