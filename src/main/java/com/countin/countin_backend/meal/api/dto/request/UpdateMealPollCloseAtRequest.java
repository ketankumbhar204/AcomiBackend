package com.countin.countin_backend.meal.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMealPollCloseAtRequest {

    @NotNull
    private LocalDateTime pollCloseAt;
}
