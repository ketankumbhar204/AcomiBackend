package com.amico.amico_backend.meal.api.dto.response;

import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealPollMySelectionResponse {

    private UUID optionId;
    private int quantity;
}
