package com.acomi.acomi_backend.space.api.dto.response;

import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "The logged-in user's default space")
public class DefaultSpaceResponse {

    private UUID spaceId;
    private String spaceName;

    @Schema(description = "Space category", implementation = SpaceType.class)
    private SpaceType spaceType;

    public static DefaultSpaceResponse from(SpaceMembershipEntity membership) {
        return DefaultSpaceResponse.builder()
                .spaceId(membership.getSpace().getId())
                .spaceName(membership.getSpace().getName())
                .spaceType(membership.getSpace().getType())
                .build();
    }
}
