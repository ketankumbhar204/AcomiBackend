package com.acomi.acomi_backend.member.api.dto.response;

import com.acomi.acomi_backend.member.domain.model.InvitationStatus;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.InvitationEntity;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InvitationResponse {

    private UUID id;
    private UUID spaceId;
    private String spaceName;
    private UUID invitedByUserId;
    private String mobileNumber;
    private MembershipRole role;
    private InvitationStatus status;
    private LocalDateTime expiresAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime createdAt;

    public static InvitationResponse from(InvitationEntity invitation) {
        return InvitationResponse.builder()
                .id(invitation.getId())
                .spaceId(invitation.getSpace().getId())
                .spaceName(invitation.getSpace().getName())
                .invitedByUserId(invitation.getInvitedBy().getId())
                .mobileNumber(invitation.getMobileNumber())
                .role(invitation.getRole())
                .status(invitation.getStatus())
                .expiresAt(invitation.getExpiresAt())
                .acceptedAt(invitation.getAcceptedAt())
                .createdAt(invitation.getCreatedAt())
                .build();
    }
}
