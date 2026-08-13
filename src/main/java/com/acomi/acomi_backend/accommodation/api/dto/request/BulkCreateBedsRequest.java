package com.acomi.acomi_backend.accommodation.api.dto.request;

import com.acomi.acomi_backend.accommodation.domain.model.BedLabelStyle;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Bulk create beds in a room")
public class BulkCreateBedsRequest {

    @NotNull(message = "Count is required")
    @Min(value = 1, message = "At least one bed is required")
    @Schema(description = "Number of beds to create", example = "3")
    private Integer count;

    @NotNull(message = "Label style is required")
    @Schema(description = "Bed label style", implementation = BedLabelStyle.class)
    private BedLabelStyle labelStyle;
}
