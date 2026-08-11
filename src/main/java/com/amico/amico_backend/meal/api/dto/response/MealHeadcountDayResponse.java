package com.amico.amico_backend.meal.api.dto.response;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealHeadcountDayResponse {

    private LocalDate date;
    private List<MealHeadcountSlotResponse> slots;
}
