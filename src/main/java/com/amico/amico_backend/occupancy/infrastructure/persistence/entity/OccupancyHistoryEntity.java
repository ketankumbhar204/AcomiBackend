package com.amico.amico_backend.occupancy.infrastructure.persistence.entity;

import com.amico.amico_backend.common.model.BaseEntity;
import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.amico.amico_backend.occupancy.domain.model.AllocationTargetType;
import com.amico.amico_backend.occupancy.domain.model.OccupancyHistoryEvent;
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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "occupancy_history",
        indexes = {
            @Index(name = "idx_occupancy_history_occupancy_id", columnList = "occupancy_id"),
            @Index(name = "idx_occupancy_history_member_id", columnList = "member_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OccupancyHistoryEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "occupancy_id", nullable = false)
    private OccupancyEntity occupancy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "space_id", nullable = false)
    private SpaceEntity space;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private MemberEntity member;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private OccupancyHistoryEvent eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_target_type", length = 10)
    private AllocationTargetType fromTargetType;

    @Column(name = "from_building_id")
    private UUID fromBuildingId;

    @Column(name = "from_floor_id")
    private UUID fromFloorId;

    @Column(name = "from_unit_id")
    private UUID fromUnitId;

    @Column(name = "from_room_id")
    private UUID fromRoomId;

    @Column(name = "from_bed_id")
    private UUID fromBedId;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_target_type", length = 10)
    private AllocationTargetType toTargetType;

    @Column(name = "to_building_id")
    private UUID toBuildingId;

    @Column(name = "to_floor_id")
    private UUID toFloorId;

    @Column(name = "to_unit_id")
    private UUID toUnitId;

    @Column(name = "to_room_id")
    private UUID toRoomId;

    @Column(name = "to_bed_id")
    private UUID toBedId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performed_by", nullable = false)
    private UserEntity performedBy;

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;

    @Column(columnDefinition = "TEXT")
    private String remarks;
}
