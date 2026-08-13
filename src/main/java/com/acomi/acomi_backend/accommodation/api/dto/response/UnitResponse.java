package com.acomi.acomi_backend.accommodation.api.dto.response;

import com.acomi.acomi_backend.accommodation.domain.model.AccommodationStatus;
import com.acomi.acomi_backend.accommodation.domain.model.UnitKind;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.UnitEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Unit within a building")
public class UnitResponse {

    private UUID unitId;
    private UUID buildingId;
    private UUID floorId;
    private String name;
    private String unitNumber;
    private AccommodationStatus status;
    private boolean synthetic;
    private UnitKind unitKind;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private AccommodationActionMetadata actions;
    private BigDecimal defaultRent;
    private BigDecimal defaultDeposit;

    public static UnitResponse from(UnitEntity unit) {
        return from(unit, null);
    }

    public static UnitResponse from(UnitEntity unit, AccommodationActionMetadata actions) {
        return UnitResponse.builder()
                .unitId(unit.getId())
                .buildingId(unit.getBuilding().getId())
                .floorId(unit.getFloor() != null ? unit.getFloor().getId() : null)
                .name(unit.getName())
                .unitNumber(unit.getUnitNumber())
                .status(unit.getStatus())
                .synthetic(unit.isSynthetic())
                .unitKind(unit.getUnitKind())
                .active(unit.isActive())
                .createdAt(unit.getCreatedAt())
                .updatedAt(unit.getUpdatedAt())
                .actions(actions)
                .defaultRent(unit.getDefaultRent())
                .defaultDeposit(unit.getDefaultDeposit())
                .build();
    }
}
