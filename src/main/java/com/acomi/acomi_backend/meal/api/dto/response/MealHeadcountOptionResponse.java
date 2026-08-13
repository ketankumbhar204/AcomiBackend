package com.acomi.acomi_backend.meal.api.dto.response;

import com.acomi.acomi_backend.meal.domain.model.MealPollOptionType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealHeadcountOptionResponse {

    private UUID optionId;
    private MealPollOptionType optionType;
    private int sortOrder;
    private String label;
    private String detail;
    private BigDecimal price;
    private String currencyCode;
    private int count;
    private List<MealHeadcountMemberResponse> members;
}
