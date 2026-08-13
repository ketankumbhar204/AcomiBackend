package com.acomi.acomi_backend.space.api.dto.response;

import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Space summary for a user membership")
public class UserSpaceResponse {

    private UUID spaceId;
    private String spaceName;

    @Schema(description = "Space address; use to disambiguate spaces with the same name")
    private String address;

    @Schema(description = "Space category", implementation = SpaceType.class)
    private SpaceType spaceType;

    @Schema(description = "Role of the user in this space")
    private MembershipRole membershipRole;

    private LocalDateTime joinedAt;

    public static UserSpaceResponse from(SpaceMembershipEntity membership) {
        return UserSpaceResponse.builder()
                .spaceId(membership.getSpace().getId())
                .spaceName(membership.getSpace().getName())
                .address(membership.getSpace().getAddress())
                .spaceType(membership.getSpace().getType())
                .membershipRole(membership.getRole())
                .joinedAt(membership.getJoinedAt())
                .build();
    }
}
