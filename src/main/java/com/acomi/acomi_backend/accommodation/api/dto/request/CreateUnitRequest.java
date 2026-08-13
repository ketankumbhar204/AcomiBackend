package com.acomi.acomi_backend.accommodation.api.dto.request;

import com.acomi.acomi_backend.accommodation.domain.model.AccommodationStatus;
import com.acomi.acomi_backend.accommodation.domain.model.UnitKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Request body for creating a unit")
public class CreateUnitRequest {

    @NotBlank(message = "Unit name is required")
    @Schema(description = "Display name of the unit", example = "Flat 101")
    private String name;

    @NotBlank(message = "Unit number is required")
    @Schema(description = "Unit identifier", example = "101")
    private String unitNumber;

    @Schema(description = "Accommodation status; defaults to AVAILABLE if omitted")
    private AccommodationStatus status;

    @Schema(description = "Optional apartment classification", implementation = UnitKind.class)
    private UnitKind unitKind;
}
