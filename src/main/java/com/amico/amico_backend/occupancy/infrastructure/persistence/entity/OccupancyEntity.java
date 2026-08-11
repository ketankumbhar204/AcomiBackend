package com.amico.amico_backend.occupancy.infrastructure.persistence.entity;

import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.FloorEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.UnitEntity;
import com.amico.amico_backend.common.model.BaseEntity;
import com.amico.amico_backend.member.domain.model.MemberCategory;
import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.amico.amico_backend.occupancy.domain.model.AllocationTargetType;
import com.amico.amico_backend.occupancy.domain.model.OccupancyStatus;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.amico.amico_backend.user.infrastructure.persistence.entity.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "occupancies",
        indexes = {
            @Index(name = "idx_occupancies_space_id", columnList = "space_id"),
            @Index(name = "idx_occupancies_member_id", columnList = "member_id"),
            @Index(name = "idx_occupancies_building_id", columnList = "building_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OccupancyEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "space_id", nullable = false)
    private SpaceEntity space;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private MemberEntity member;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private AllocationTargetType targetType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "building_id", nullable = false)
    private BuildingEntity building;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id")
    private FloorEntity floor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private UnitEntity unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private RoomEntity room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bed_id")
    private BedEntity bed;

    @Column(name = "allocated_at", nullable = false)
    private LocalDateTime allocatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "allocated_by", nullable = false)
    private UserEntity allocatedBy;

    @Column(name = "expected_checkout_date")
    @Deprecated
    private LocalDate expectedCheckoutDate;

    @Column(name = "reserved_at")
    private LocalDateTime reservedAt;

    @Column(name = "move_in_date", nullable = false)
    private LocalDate moveInDate;

    @Column(name = "actual_move_in_at")
    private LocalDateTime actualMoveInAt;

    @Column(name = "expected_exit_date")
    private LocalDate expectedExitDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_category", length = 30)
    private MemberCategory memberCategory;

    @Builder.Default
    @Column(name = "agreement_signed", nullable = false)
    private boolean agreementSigned = false;

    @Column(name = "rent_snapshot", precision = 12, scale = 2)
    private BigDecimal rentSnapshot;

    @Builder.Default
    @Column(name = "deposit_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositSnapshot = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "food_enabled", nullable = false)
    private boolean foodEnabled = false;

    @Column(name = "food_charge_snapshot", precision = 12, scale = 2)
    private BigDecimal foodChargeSnapshot;

    @Builder.Default
    @Column(name = "food_included_in_rent", nullable = false)
    private boolean foodIncludedInRent = false;

    @Column(name = "pricing_locked_at")
    private LocalDateTime pricingLockedAt;

    @Builder.Default
    @OneToMany(mappedBy = "occupancy")
    private List<OccupancyChargeSnapshotEntity> chargeSnapshots = new ArrayList<>();

    @Column(name = "vacated_at")
    private LocalDateTime vacatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vacated_by")
    private UserEntity vacatedBy;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OccupancyStatus status = OccupancyStatus.ACTIVE;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private UserEntity createdBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "updated_by", nullable = false)
    private UserEntity updatedBy;
}
