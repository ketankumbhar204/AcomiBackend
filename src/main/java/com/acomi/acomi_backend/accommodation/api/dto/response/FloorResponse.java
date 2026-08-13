package com.acomi.acomi_backend.accommodation.api.dto.response;

import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.FloorEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Floor within a building")
public class FloorResponse {

    private UUID floorId;
    private UUID buildingId;
    private String name;
    private int floorNumber;
    private int sortOrder;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private AccommodationActionMetadata actions;

    public static FloorResponse from(FloorEntity floor) {
        return from(floor, null);
    }

    public static FloorResponse from(FloorEntity floor, AccommodationActionMetadata actions) {
        return FloorResponse.builder()
                .floorId(floor.getId())
                .buildingId(floor.getBuilding().getId())
                .name(floor.getName())
                .floorNumber(floor.getFloorNumber())
                .sortOrder(floor.getSortOrder())
                .active(floor.isActive())
                .createdAt(floor.getCreatedAt())
                .updatedAt(floor.getUpdatedAt())
                .actions(actions)
                .build();
    }
}
