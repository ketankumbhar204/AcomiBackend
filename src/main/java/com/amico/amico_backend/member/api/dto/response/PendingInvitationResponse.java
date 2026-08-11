package com.amico.amico_backend.member.api.dto.response;

import com.amico.amico_backend.member.domain.model.InvitationStatus;
import com.amico.amico_backend.member.domain.model.MembershipRole;
import com.amico.amico_backend.member.infrastructure.persistence.entity.InvitationEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Pending invitation for a space")
public class PendingInvitationResponse {

    private UUID invitationId;
    private String mobileNumber;

    @Schema(description = "Role offered by the invitation")
    private MembershipRole role;

    private InvitationStatus status;
    private String invitedBy;
    private LocalDateTime createdAt;

    public static PendingInvitationResponse from(InvitationEntity invitation) {
        return PendingInvitationResponse.builder()
                .invitationId(invitation.getId())
                .mobileNumber(invitation.getMobileNumber())
                .role(invitation.getRole())
                .status(invitation.getStatus())
                .invitedBy(invitation.getInvitedBy().getFullName())
                .createdAt(invitation.getCreatedAt())
                .build();
    }
}
