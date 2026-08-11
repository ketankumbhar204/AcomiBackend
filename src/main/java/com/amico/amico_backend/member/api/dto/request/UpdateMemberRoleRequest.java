package com.amico.amico_backend.member.api.dto.request;

import com.amico.amico_backend.member.domain.model.MembershipRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Request body for updating a member's role")
public class UpdateMemberRoleRequest {

    @NotNull(message = "Role is required")
    @Schema(description = "New role for the member", example = "MANAGER", implementation = MembershipRole.class)
    private MembershipRole role;
}
