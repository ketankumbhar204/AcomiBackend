package com.acomi.acomi_backend.meal.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmitMealPollOptionQuantityRequest {

    @NotNull
    private UUID optionId;

    @NotNull
    @Min(0)
    private Integer quantity;
}
