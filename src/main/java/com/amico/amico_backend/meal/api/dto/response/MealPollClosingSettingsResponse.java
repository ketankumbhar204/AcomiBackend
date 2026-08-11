package com.amico.amico_backend.meal.api.dto.response;

import com.amico.amico_backend.meal.domain.model.PollCloseDayOffset;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.time.LocalTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealPollClosingSettingsResponse {

    private String timezone;
    private PollCloseDayOffset breakfastDayOffset;
    private LocalTime breakfastTime;
    private PollCloseDayOffset lunchDayOffset;
    private LocalTime lunchTime;
    private PollCloseDayOffset dinnerDayOffset;
    private LocalTime dinnerTime;

    public static MealPollClosingSettingsResponse from(SpaceEntity space) {
        return MealPollClosingSettingsResponse.builder()
                .timezone(space.getTimezone())
                .breakfastDayOffset(space.getPollCloseBreakfastDayOffset())
                .breakfastTime(space.getPollCloseBreakfastTime())
                .lunchDayOffset(space.getPollCloseLunchDayOffset())
                .lunchTime(space.getPollCloseLunchTime())
                .dinnerDayOffset(space.getPollCloseDinnerDayOffset())
                .dinnerTime(space.getPollCloseDinnerTime())
                .build();
    }
}
