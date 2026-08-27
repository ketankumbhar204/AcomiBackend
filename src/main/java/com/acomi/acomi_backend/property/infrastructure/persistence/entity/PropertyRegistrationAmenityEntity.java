package com.acomi.acomi_backend.property.infrastructure.persistence.entity;

import com.acomi.acomi_backend.common.model.BaseEntity;
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
        name = "property_registration_amenities",
        indexes =
                @Index(
                        name = "idx_property_registration_amenities_registration_id",
                        columnList = "property_registration_id"))
public class PropertyRegistrationAmenityEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_registration_id", nullable = false)
    private PropertyRegistrationEntity propertyRegistration;

    @Column(name = "amenity_code", nullable = false, length = 50)
    private String amenityCode;

    @Column(name = "custom_label", length = 120)
    private String customLabel;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
