package com.countin.countin_backend.occupancy.api.dto.response;

import com.countin.countin_backend.member.domain.model.MemberCategory;
import com.countin.countin_backend.occupancy.domain.model.AllocationTargetType;
import com.countin.countin_backend.occupancy.domain.model.OccupancyStatus;
import com.countin.countin_backend.occupancy.infrastructure.persistence.entity.OccupancyChargeSnapshotEntity;
import com.countin.countin_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.countin.countin_backend.space.api.dto.AmenityAssignmentDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Occupancy assignment record")
public class OccupancyResponse {

    private UUID occupancyId;
    private UUID spaceId;
    private UUID memberId;
    private String memberName;
    private AllocationTargetType targetType;
    private UUID buildingId;
    private String buildingName;
    private UUID floorId;
    private String floorName;
    private UUID unitId;
    private String unitName;
    private UUID roomId;
    private String roomName;
    private UUID bedId;
    private String bedName;
    private LocalDateTime allocatedAt;
    private UUID allocatedBy;
    @Deprecated
    private LocalDate expectedCheckoutDate;
    private LocalDateTime reservedAt;
    private LocalDate moveInDate;
    private LocalDateTime actualMoveInAt;
    private LocalDate expectedExitDate;
    private MemberCategory memberCategory;
    private boolean agreementSigned;
    private BigDecimal rentSnapshot;
    private BigDecimal depositSnapshot;
    private boolean foodEnabled;
    private BigDecimal foodChargeSnapshot;
    private boolean foodIncludedInRent;
    private LocalDateTime pricingLockedAt;
    private List<OccupancyChargeLineResponse> otherCharges;
    private List<AmenityAssignmentDto> amenities;
    private LocalDateTime vacatedAt;
    private UUID vacatedBy;
    private OccupancyStatus status;
    private String remarks;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static OccupancyResponse from(OccupancyEntity entity) {
        return from(entity, List.of(), List.of());
    }

    public static OccupancyResponse from(
            OccupancyEntity entity, List<OccupancyChargeSnapshotEntity> chargeSnapshots) {
        return from(entity, chargeSnapshots, List.of());
    }

    public static OccupancyResponse from(
            OccupancyEntity entity,
            List<OccupancyChargeSnapshotEntity> chargeSnapshots,
            List<AmenityAssignmentDto> amenities) {
        LocalDate expectedExit = entity.getExpectedExitDate() != null
                ? entity.getExpectedExitDate()
                : entity.getExpectedCheckoutDate();

        return OccupancyResponse.builder()
                .occupancyId(entity.getId())
                .spaceId(entity.getSpace().getId())
                .memberId(entity.getMember().getId())
                .memberName(entity.getMember().getFullName())
                .targetType(entity.getTargetType())
                .buildingId(entity.getBuilding().getId())
                .buildingName(entity.getBuilding().getName())
                .floorId(entity.getFloor() != null ? entity.getFloor().getId() : null)
                .floorName(entity.getFloor() != null ? entity.getFloor().getName() : null)
                .unitId(entity.getUnit() != null ? entity.getUnit().getId() : null)
                .unitName(entity.getUnit() != null ? entity.getUnit().getName() : null)
                .roomId(entity.getRoom() != null ? entity.getRoom().getId() : null)
                .roomName(entity.getRoom() != null ? entity.getRoom().getName() : null)
                .bedId(entity.getBed() != null ? entity.getBed().getId() : null)
                .bedName(entity.getBed() != null ? entity.getBed().getName() : null)
                .allocatedAt(entity.getAllocatedAt())
                .allocatedBy(entity.getAllocatedBy().getId())
                .expectedCheckoutDate(expectedExit)
                .reservedAt(entity.getReservedAt())
                .moveInDate(entity.getMoveInDate())
                .actualMoveInAt(entity.getActualMoveInAt())
                .expectedExitDate(expectedExit)
                .memberCategory(entity.getMemberCategory())
                .agreementSigned(entity.isAgreementSigned())
                .rentSnapshot(entity.getRentSnapshot())
                .depositSnapshot(entity.getDepositSnapshot())
                .foodEnabled(entity.isFoodEnabled())
                .foodChargeSnapshot(entity.getFoodChargeSnapshot())
                .foodIncludedInRent(entity.isFoodIncludedInRent())
                .pricingLockedAt(entity.getPricingLockedAt())
                .otherCharges(chargeSnapshots.stream().map(OccupancyChargeLineResponse::from).toList())
                .amenities(amenities)
                .vacatedAt(entity.getVacatedAt())
                .vacatedBy(entity.getVacatedBy() != null ? entity.getVacatedBy().getId() : null)
                .status(entity.getStatus())
                .remarks(entity.getRemarks())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
