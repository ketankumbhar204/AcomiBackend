package com.amico.amico_backend.accommodation.infrastructure.persistence.projection;

import com.amico.amico_backend.accommodation.domain.model.AccommodationStatus;
import com.amico.amico_backend.occupancy.domain.model.AllocationTargetType;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;

@Getter
public class AllocationTargetSearchRow {

    private final AllocationTargetType targetType;
    private final UUID targetId;
    private final UUID buildingId;
    private final String buildingName;
    private final String buildingCode;
    private final UUID floorId;
    private final String floorName;
    private final UUID unitId;
    private final String unitName;
    private final String unitNumber;
    private final UUID roomId;
    private final String roomName;
    private final String roomNumber;
    private final UUID bedId;
    private final String bedName;
    private final String bedNumber;
    private final AccommodationStatus status;
    private final BigDecimal defaultRent;
    private final BigDecimal defaultDeposit;

    public AllocationTargetSearchRow(
            AllocationTargetType targetType,
            UUID targetId,
            UUID buildingId,
            String buildingName,
            String buildingCode,
            UUID floorId,
            String floorName,
            UUID unitId,
            String unitName,
            String unitNumber,
            UUID roomId,
            String roomName,
            String roomNumber,
            UUID bedId,
            String bedName,
            String bedNumber,
            AccommodationStatus status,
            BigDecimal defaultRent,
            BigDecimal defaultDeposit) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.buildingId = buildingId;
        this.buildingName = buildingName;
        this.buildingCode = buildingCode;
        this.floorId = floorId;
        this.floorName = floorName;
        this.unitId = unitId;
        this.unitName = unitName;
        this.unitNumber = unitNumber;
        this.roomId = roomId;
        this.roomName = roomName;
        this.roomNumber = roomNumber;
        this.bedId = bedId;
        this.bedName = bedName;
        this.bedNumber = bedNumber;
        this.status = status;
        this.defaultRent = defaultRent;
        this.defaultDeposit = defaultDeposit;
    }
}
