package com.amico.amico_backend.inventory.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateInventoryCategoryRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 40)
    private String iconKey;
}
