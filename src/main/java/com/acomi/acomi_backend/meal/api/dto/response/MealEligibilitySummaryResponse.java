package com.acomi.acomi_backend.meal.api.dto.response;

import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealEligibilitySummaryResponse {

    private LocalDate date;
    /** Unique members eligible for at least one meal slot on this date (not summed across slots). */
    private int distinctEligibleMemberCount;
    private List<MealEligibilitySlotResponse> slots;
}
