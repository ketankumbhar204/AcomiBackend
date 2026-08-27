package com.acomi.acomi_backend.mess.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Confirmation for a submitted mess registration")
public class MessRegistrationResponse {

    @Schema(description = "Human-readable reference to quote in support conversations",
            example = "MR-2026-000123")
    private final String reference;

    @Schema(description = "When the registration was received")
    private final LocalDateTime submittedAt;
}
