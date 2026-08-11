package com.amico.amico_backend.member.api.dto.response;

import com.amico.amico_backend.member.domain.model.MembershipRole;
import com.amico.amico_backend.member.domain.model.MembershipStatus;
import com.amico.amico_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SpaceMembershipResponse {

    private UUID id;
    private UUID spaceId;
    private String spaceName;
    private UUID userId;
    private MembershipRole role;
    private MembershipStatus status;
    private LocalDateTime joinedAt;
    private LocalDateTime createdAt;

    public static SpaceMembershipResponse from(SpaceMembershipEntity membership) {
        return SpaceMembershipResponse.builder()
                .id(membership.getId())
                .spaceId(membership.getSpace().getId())
                .spaceName(membership.getSpace().getName())
                .userId(membership.getUser().getId())
                .role(membership.getRole())
                .status(membership.getStatus())
                .joinedAt(membership.getJoinedAt())
                .createdAt(membership.getCreatedAt())
                .build();
    }
}
