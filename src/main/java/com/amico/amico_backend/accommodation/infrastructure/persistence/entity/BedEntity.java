package com.amico.amico_backend.accommodation.infrastructure.persistence.entity;

import com.amico.amico_backend.accommodation.domain.model.AccommodationStatus;
import com.amico.amico_backend.common.model.BaseEntity;
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
        name = "beds",
        indexes = {@Index(name = "idx_beds_room_id", columnList = "room_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BedEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private RoomEntity room;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "bed_number", nullable = false, length = 20)
    private String bedNumber;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccommodationStatus status = AccommodationStatus.AVAILABLE;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "default_rent", precision = 12, scale = 2)
    private BigDecimal defaultRent;

    @Column(name = "default_deposit", precision = 12, scale = 2)
    private BigDecimal defaultDeposit;
}
