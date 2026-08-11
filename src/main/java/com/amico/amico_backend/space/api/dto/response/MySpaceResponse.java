package com.amico.amico_backend.space.api.dto.response;

import com.amico.amico_backend.member.domain.model.MembershipRole;
import com.amico.amico_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.amico.amico_backend.space.application.policy.SpacePermissionPolicy;
import com.amico.amico_backend.space.domain.model.SpaceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Space entry in the logged-in user's space list")
public class MySpaceResponse {

    private UUID spaceId;
    private String spaceName;

    @Schema(description = "Space address; use to disambiguate spaces with the same name")
    private String address;

    @Schema(description = "Space category", implementation = SpaceType.class)
    private SpaceType spaceType;

    @Schema(description = "Role of the user in this space")
    private MembershipRole membershipRole;

    @Schema(description = "Whether this is the user's default space")
    private boolean isDefault;

    private LocalDateTime joinedAt;

    @Schema(description = "Computed capabilities for the caller in this space")
    private SpacePermissionsResponse permissions;

    public static MySpaceResponse from(SpaceMembershipEntity membership) {
        return MySpaceResponse.builder()
                .spaceId(membership.getSpace().getId())
                .spaceName(membership.getSpace().getName())
                .address(membership.getSpace().getAddress())
                .spaceType(membership.getSpace().getType())
                .membershipRole(membership.getRole())
                .isDefault(membership.isDefault())
                .joinedAt(membership.getJoinedAt())
                .permissions(SpacePermissionPolicy.forMembership(membership))
                .build();
    }
}
