package com.amico.amico_backend.member.api.dto.request;

import com.amico.amico_backend.member.domain.model.MembershipRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateInvitationRequest {

    @NotNull(message = "Space ID is required")
    private UUID spaceId;

    @NotNull(message = "Invited by user ID is required")
    private UUID invitedByUserId;

    @NotBlank(message = "Mobile number is required")
    private String mobileNumber;

    @NotNull(message = "Role is required")
    private MembershipRole role;
}
