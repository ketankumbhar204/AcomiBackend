package com.amico.amico_backend.meal.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateSubscriptionPlanRequest {

    @NotBlank
    private String name;

    @NotNull
    @Min(1)
    private Integer mealsIncluded;

    @NotNull
    private BigDecimal price;

    private String currencyCode;

    @NotNull
    @Min(1)
    private Integer validityDays;

    private Boolean carryForwardUnused;

    private String description;

    private Integer sortOrder;
}
