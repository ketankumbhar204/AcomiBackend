package com.acomi.acomi_backend.meal.api.dto.response;

import com.acomi.acomi_backend.meal.domain.model.MealParticipationHistoryAction;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealParticipationHistoryEntryResponse {

    private UUID id;
    private MealParticipationHistoryAction action;
    private String oldValue;
    private String newValue;
    private UUID changedBy;
    private LocalDateTime changedAt;
}
