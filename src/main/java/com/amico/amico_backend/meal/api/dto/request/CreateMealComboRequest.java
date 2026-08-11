package com.amico.amico_backend.meal.api.dto.request;

import com.amico.amico_backend.meal.domain.model.FoodType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateMealComboRequest {

    @NotBlank
    private String name;

    private String description;

    private BigDecimal price;

    private String currencyCode;

    private FoodType foodType;

    private List<UUID> itemIds = new ArrayList<>();

    /**
     * Optional per-item quantities (Mess). Keys not listed default to 1.
     * Ignored for non-Mess spaces (always stored as 1).
     */
    @Valid
    private List<MealComboItemQuantityRequest> itemQuantities = new ArrayList<>();

    @Valid
    private List<CreateComboInlineItemRequest> newItems = new ArrayList<>();
}
