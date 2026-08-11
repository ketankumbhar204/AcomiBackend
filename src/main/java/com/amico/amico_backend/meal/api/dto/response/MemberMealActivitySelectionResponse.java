package com.amico.amico_backend.meal.api.dto.response;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberMealActivitySelectionResponse {

    private String label;
    private BigDecimal price;
    private String currencyCode;
    private int quantity;
    private String itemDetail;
    private BigDecimal lineTotal;
}
