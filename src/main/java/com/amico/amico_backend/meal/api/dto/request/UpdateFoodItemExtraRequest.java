package com.amico.amico_backend.meal.api.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateFoodItemExtraRequest {

    @JsonProperty("isExtra")
    private boolean extra;
}
