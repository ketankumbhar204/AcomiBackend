package com.amico.amico_backend.member.api.dto.response;

import com.amico.amico_backend.member.domain.model.MembershipRole;
import com.amico.amico_backend.member.infrastructure.persistence.entity.InvitationEntity;
import com.amico.amico_backend.space.domain.model.SpaceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Pending invitation for the signed-in user")
public class MyInvitationResponse {

    private UUID invitationId;
    private UUID spaceId;
    private String spaceName;
    private SpaceType spaceType;
    private MembershipRole role;
    private String invitedBy;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    public static MyInvitationResponse from(InvitationEntity invitation) {
        return MyInvitationResponse.builder()
                .invitationId(invitation.getId())
                .spaceId(invitation.getSpace().getId())
                .spaceName(invitation.getSpace().getName())
                .spaceType(invitation.getSpace().getType())
                .role(invitation.getRole())
                .invitedBy(invitation.getInvitedBy().getFullName())
                .createdAt(invitation.getCreatedAt())
                .expiresAt(invitation.getExpiresAt())
                .build();
    }
}
