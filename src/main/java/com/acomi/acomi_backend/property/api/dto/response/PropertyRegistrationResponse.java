package com.acomi.acomi_backend.property.api.dto.response;

import com.acomi.acomi_backend.property.domain.model.PriceBasis;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * Deliberately minimal. The public website only needs the reference to show a confirmation, so
 * internal review fields are not exposed to an unauthenticated caller.
 */
@Getter
@Builder
@Schema(description = "Confirmation for a submitted property registration")
public class PropertyRegistrationResponse {

    @Schema(description = "Human-readable reference to quote in support conversations",
            example = "PR-2026-000123")
    private final String reference;

    @Schema(description = "Server-derived price basis", example = "PER_BED")
    private final PriceBasis priceBasis;

    @Schema(description = "When the registration was received")
    private final LocalDateTime submittedAt;
}
