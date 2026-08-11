package com.amico.amico_backend.occupancy.infrastructure.persistence.entity;

import com.amico.amico_backend.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "occupancy_amenities",
        indexes = @Index(name = "idx_occupancy_amenities_occupancy_id", columnList = "occupancy_id"))
public class OccupancyAmenityEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "occupancy_id", nullable = false)
    private OccupancyEntity occupancy;

    @Column(name = "amenity_code", nullable = false, length = 50)
    private String amenityCode;

    @Column(name = "custom_label", length = 120)
    private String customLabel;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
