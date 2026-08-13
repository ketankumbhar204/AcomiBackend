package com.acomi.acomi_backend.inventory.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateInventorySupplierRequest {

    @NotBlank
    @Size(max = 150)
    private String name;

    @Size(max = 30)
    private String phone;

    private String address;

    private String notes;
}
