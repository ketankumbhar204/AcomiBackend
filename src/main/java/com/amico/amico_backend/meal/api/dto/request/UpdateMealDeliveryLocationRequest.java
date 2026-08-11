package com.amico.amico_backend.meal.api.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMealDeliveryLocationRequest {

    @Size(max = 120)
    private String name;

    @Size(max = 500)
    private String description;

    @Size(max = 500)
    private String address;

    private Boolean active;

    private Integer sortOrder;
}
