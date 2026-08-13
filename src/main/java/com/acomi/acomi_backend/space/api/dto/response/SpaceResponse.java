package com.acomi.acomi_backend.space.api.dto.response;

import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Space details returned after creation or lookup")
public class SpaceResponse {

    private UUID id;

    @Schema(example = "Sunrise Apartments")
    private String name;

    @Schema(description = "Space category", example = "RENTAL", implementation = SpaceType.class)
    private SpaceType type;
    private String address;
    private String contactNumber;
    private boolean isActive;
    private UUID ownerId;
    private String ownerName;
    private LocalDateTime createdAt;

    public static SpaceResponse from(SpaceEntity space) {
        return SpaceResponse.builder()
                .id(space.getId())
                .name(space.getName())
                .type(space.getType())
                .address(space.getAddress())
                .contactNumber(space.getContactNumber())
                .isActive(space.isActive())
                .ownerId(space.getOwner().getId())
                .ownerName(space.getOwner().getFullName())
                .createdAt(space.getCreatedAt())
                .build();
    }
}
