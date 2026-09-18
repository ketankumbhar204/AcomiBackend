package com.acomi.acomi_backend.inquirycredit.api.dto.request;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateCreditPackageRequest {

    private String name;
    private BigDecimal priceAmount;
    private String currency;
    private Integer credits;
    private Boolean enabled;
    private Integer displayOrder;
}
