package com.countin.countin_backend.meal.api.dto.request;

import com.countin.countin_backend.meal.domain.model.PollCloseDayOffset;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMealPollClosingSettingsRequest {

    @NotBlank
    private String timezone;

    @NotNull
    private PollCloseDayOffset breakfastDayOffset;

    @NotNull
    private LocalTime breakfastTime;

    @NotNull
    private PollCloseDayOffset lunchDayOffset;

    @NotNull
    private LocalTime lunchTime;

    @NotNull
    private PollCloseDayOffset dinnerDayOffset;

    @NotNull
    private LocalTime dinnerTime;
}
