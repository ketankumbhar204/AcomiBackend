package com.amico.amico_backend.accommodation.api.dto.response;

import com.amico.amico_backend.accommodation.domain.model.AccommodationStatus;
import com.amico.amico_backend.accommodation.domain.model.RoomType;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Room within a floor or unit")
public class RoomResponse {

    private UUID roomId;
    private UUID floorId;
    private UUID unitId;

    @Schema(description = "Parent building when room is under a floor or unit")
    private UUID buildingId;
    private String name;
    private String roomNumber;
    private RoomType roomType;
    private int capacity;
    private AccommodationStatus status;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private AccommodationActionMetadata actions;
    private BigDecimal defaultRent;
    private BigDecimal defaultDeposit;

    public static RoomResponse from(RoomEntity room) {
        return from(room, null);
    }

    public static RoomResponse from(RoomEntity room, AccommodationActionMetadata actions) {
        return RoomResponse.builder()
                .roomId(room.getId())
                .floorId(room.getFloor() != null ? room.getFloor().getId() : null)
                .unitId(room.getUnit() != null ? room.getUnit().getId() : null)
                .buildingId(resolveBuildingId(room))
                .name(room.getName())
                .roomNumber(room.getRoomNumber())
                .roomType(room.getRoomType())
                .capacity(room.getCapacity())
                .status(room.getStatus())
                .active(room.isActive())
                .createdAt(room.getCreatedAt())
                .updatedAt(room.getUpdatedAt())
                .actions(actions)
                .defaultRent(room.getDefaultRent())
                .defaultDeposit(room.getDefaultDeposit())
                .build();
    }

    private static UUID resolveBuildingId(RoomEntity room) {
        if (room.getFloor() != null && room.getFloor().getBuilding() != null) {
            return room.getFloor().getBuilding().getId();
        }
        if (room.getUnit() != null && room.getUnit().getBuilding() != null) {
            return room.getUnit().getBuilding().getId();
        }
        return null;
    }
}
