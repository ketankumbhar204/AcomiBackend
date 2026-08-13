package com.acomi.acomi_backend.space.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Amenity assignment with stable code and display label")
public class AmenityAssignmentDto {

    @Schema(description = "Stable amenity code such as WIFI or CUSTOM", example = "WIFI")
    private String code;

    @Schema(description = "Display label shown in the app", example = "WiFi")
    private String label;
}
