package com.acomi.acomi_backend.accommodation.api.dto.request;

import com.acomi.acomi_backend.accommodation.domain.model.AccommodationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Request body for creating a bed")
public class CreateBedRequest {

    @NotBlank(message = "Bed name is required")
    @Schema(description = "Display name of the bed", example = "Bed A")
    private String name;

    @NotBlank(message = "Bed number is required")
    @Schema(description = "Bed identifier within the room", example = "A")
    private String bedNumber;

    @Schema(description = "Accommodation status", implementation = AccommodationStatus.class)
    private AccommodationStatus status;

    @DecimalMin(value = "0.0", inclusive = true)
    @Schema(description = "Optional default monthly rent for this bed")
    private BigDecimal defaultRent;

    @DecimalMin(value = "0.0", inclusive = true)
    @Schema(description = "Optional default deposit for this bed")
    private BigDecimal defaultDeposit;
}
