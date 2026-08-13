package com.acomi.acomi_backend.meal.api.dto.request;

import com.acomi.acomi_backend.space.domain.model.MealBillingType;
import com.acomi.acomi_backend.space.domain.model.PrepaidBalanceUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Update space-level meal billing settings")
public class UpdateMealBillingSettingsRequest {

    @NotNull(message = "Billing type is required")
    @Schema(description = "PAY_PER_MEAL or PREPAID_BALANCE", example = "PAY_PER_MEAL")
    private MealBillingType billingType;

    @Schema(description = "Unit for prepaid balance — required when billingType is PREPAID_BALANCE")
    private PrepaidBalanceUnit prepaidBalanceUnit;

    @Schema(description = "When true, members without balance pay per meal automatically")
    private Boolean fallbackToPayPerMeal;
}
