package com.acomi.acomi_backend.accommodation.api.dto.response;

import com.acomi.acomi_backend.accommodation.domain.model.PropertyLayoutMode;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Building in a space")
public class BuildingResponse {

    private UUID buildingId;
    private UUID spaceId;
    private String name;
    private String code;
    private PropertyLayoutMode layoutMode;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private AccommodationActionMetadata actions;

    public static BuildingResponse from(BuildingEntity building) {
        return from(building, null);
    }

    public static BuildingResponse from(BuildingEntity building, AccommodationActionMetadata actions) {
        return BuildingResponse.builder()
                .buildingId(building.getId())
                .spaceId(building.getSpace().getId())
                .name(building.getName())
                .code(building.getCode())
                .layoutMode(building.getLayoutMode())
                .active(building.isActive())
                .createdAt(building.getCreatedAt())
                .updatedAt(building.getUpdatedAt())
                .actions(actions)
                .build();
    }
}
