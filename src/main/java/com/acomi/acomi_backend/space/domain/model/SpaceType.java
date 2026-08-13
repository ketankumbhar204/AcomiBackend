package com.acomi.acomi_backend.space.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Category of the space")
public enum SpaceType {
    @Schema(description = "Paying Guest accommodation")
    PG,
    @Schema(description = "Meal-providing canteen or mess")
    MESS,
    @Schema(description = "Hostel-style accommodation")
    HOSTEL,
    @Schema(description = "Co-living or shared accommodation space")
    CO_LIVING,
    @Schema(description = "Rental property or apartment")
    RENTAL
}
