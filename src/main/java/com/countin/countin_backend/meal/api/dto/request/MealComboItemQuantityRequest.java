package com.countin.countin_backend.meal.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Optional per-item quantity when creating/updating a combo (Mess). */
@Getter
@Setter
public class MealComboItemQuantityRequest {

    @NotNull
    private UUID itemId;

    /** Defaults to 1 when null or missing. Must be >= 1 when provided. */
    @Min(1)
    private Integer quantity;
}
