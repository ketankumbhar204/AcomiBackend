package com.amico.amico_backend.meal.api.dto.response;

import com.amico.amico_backend.meal.domain.model.DailyMenuStatus;
import com.amico.amico_backend.meal.domain.model.MealType;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.DailyMenuEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.DailyMenuEntryEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DailyMenuResponse {

    private UUID dailyMenuId;
    private LocalDate menuDate;
    private MealType mealType;
    private DailyMenuStatus status;
    private LocalDateTime publishedAt;
    private String notes;
    private List<DailyMenuOptionResponse> options;

    public static DailyMenuResponse from(DailyMenuEntity menu, List<DailyMenuEntryEntity> entries) {
        return DailyMenuResponse.builder()
                .dailyMenuId(menu.getId())
                .menuDate(menu.getMenuDate())
                .mealType(menu.getMealType())
                .status(menu.getStatus())
                .publishedAt(menu.getPublishedAt())
                .notes(menu.getNotes())
                .options(entries.stream().map(DailyMenuOptionResponse::from).toList())
                .build();
    }
}
