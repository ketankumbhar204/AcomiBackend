package com.amico.amico_backend.accommodation.api.dto.request;

import com.amico.amico_backend.accommodation.domain.model.AccommodationStatus;
import com.amico.amico_backend.accommodation.domain.model.UnitKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Request body for updating a unit")
public class UpdateUnitRequest {

    @NotBlank(message = "Unit name is required")
    @Schema(description = "Display name of the unit", example = "Flat 101")
    private String name;

    @NotBlank(message = "Unit number is required")
    @Schema(description = "Unit identifier", example = "101")
    private String unitNumber;

    @NotNull(message = "Status is required")
    @Schema(description = "Accommodation status", implementation = AccommodationStatus.class)
    private AccommodationStatus status;

    @Schema(description = "Optional apartment classification", implementation = UnitKind.class)
    private UnitKind unitKind;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal defaultRent;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal defaultDeposit;
}
