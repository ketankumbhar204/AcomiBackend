package com.acomi.acomi_backend.meal.api.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReorderMealDeliveryLocationsRequest {

    @NotEmpty
    private List<UUID> locationIdsInOrder;
}
