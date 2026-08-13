package com.acomi.acomi_backend.space.api.dto.request;

import com.acomi.acomi_backend.space.api.dto.AmenityAssignmentDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Request body for updating an existing space")
public class UpdateSpaceRequest {

    @NotBlank(message = "Space name is required")
    @Schema(description = "Display name of the space", example = "Sunrise PG")
    private String name;

    @Schema(description = "Physical address of the space", example = "Pune")
    private String address;

    @Schema(description = "Public contact number for the space", example = "9876543210")
    private String contactNumber;

    @Schema(description = "When true, food is mandatory and bundled in rentSnapshot (no separate food charge)")
    private Boolean foodIncludedInRent;

    @DecimalMin(value = "0.0", inclusive = true, message = "Default food charge must be zero or greater")
    @Schema(description = "Default monthly food charge prefill when food is billed separately")
    private BigDecimal defaultFoodCharge;

    @Valid
    @Schema(description = "Replace space amenities when provided (PG, Hostel, Co-living only)")
    private List<AmenityAssignmentDto> amenities;
}
