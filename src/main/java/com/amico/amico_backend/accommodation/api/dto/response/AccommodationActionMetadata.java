package com.amico.amico_backend.accommodation.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Allowed lifecycle actions for an accommodation entity detail view")
public class AccommodationActionMetadata {

    private boolean canEdit;
    private boolean canDeactivate;
    private boolean canRestore;
    private boolean canDelete;

    @Schema(description = "Present when canDelete is false and the caller is the space owner")
    private String deleteReason;
}
