package com.acomi.acomi_backend.property.infrastructure.persistence.entity;

import com.acomi.acomi_backend.common.model.BaseEntity;
import com.acomi.acomi_backend.property.domain.model.PriceBasis;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationSource;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus;
import com.acomi.acomi_backend.registration.domain.model.RegistrationClaimVia;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
        name = "property_registrations",
        indexes = {
            @Index(name = "idx_property_registrations_mobile", columnList = "mobile_number"),
            @Index(
                    name = "idx_property_registrations_status_created",
                    columnList = "status, created_at")
        })
public class PropertyRegistrationEntity extends BaseEntity {

    @Column(name = "reference", nullable = false, length = 20, updatable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false, length = 20)
    private SpaceType propertyType;

    @Column(name = "property_name", nullable = false, length = 150)
    private String propertyName;

    @Column(name = "owner_name", nullable = false, length = 120)
    private String ownerName;

    @Column(name = "mobile_number", nullable = false, length = 15)
    private String mobileNumber;

    @Column(name = "alternate_mobile_number", length = 15)
    private String alternateMobileNumber;

    @Column(name = "mobile_verified_at", nullable = false)
    private LocalDateTime mobileVerifiedAt;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "address_line", nullable = false, length = 255)
    private String addressLine;

    @Column(name = "city", nullable = false, length = 80)
    private String city;

    @Column(name = "state", nullable = false, length = 80)
    private String state;

    @Column(name = "pincode", nullable = false, length = 6)
    private String pincode;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "map_url", length = 512)
    private String mapUrl;

    @Column(name = "starting_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal startingPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_basis", nullable = false, length = 12)
    private PriceBasis priceBasis;

    @Column(name = "capacity_estimate")
    private Integer capacityEstimate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PropertyRegistrationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 40)
    private PropertyRegistrationSource source;

    @Column(name = "converted_space_id")
    private UUID convertedSpaceId;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @Column(name = "request_ip", length = 64)
    private String requestIp;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "claimed_via", length = 40)
    private RegistrationClaimVia claimedVia;

    @Builder.Default
    @Column(name = "test_lead", nullable = false)
    private boolean testLead = false;

    @Builder.Default
    @OneToMany(
            mappedBy = "propertyRegistration",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<PropertyRegistrationAmenityEntity> amenities = new ArrayList<>();

    public void addAmenity(PropertyRegistrationAmenityEntity amenity) {
        amenity.setPropertyRegistration(this);
        amenities.add(amenity);
    }
}
