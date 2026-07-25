package com.countin.countin_backend.member.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Import an eligible resident from another managed accommodation space")
public class ImportMemberRequest {

    @NotNull(message = "Source member id is required")
    @Schema(description = "Member id in the source space", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID sourceMemberId;
}
