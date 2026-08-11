package com.amico.amico_backend.member.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AcceptInvitationRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;
}
