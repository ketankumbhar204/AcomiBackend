package com.amico.amico_backend.member.api.dto.request;

import com.amico.amico_backend.member.domain.model.MemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Request body for updating a member's operational status")
public class UpdateMemberStatusRequest {

    @NotNull(message = "Status is required")
    @Schema(description = "Member operational status", example = "SUSPENDED", implementation = MemberStatus.class)
    private MemberStatus status;
}
