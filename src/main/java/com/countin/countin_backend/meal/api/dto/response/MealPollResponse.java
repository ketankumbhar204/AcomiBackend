package com.countin.countin_backend.meal.api.dto.response;

import com.countin.countin_backend.meal.domain.model.MealPollCloseSource;
import com.countin.countin_backend.meal.domain.model.MealPollStatus;
import com.countin.countin_backend.meal.domain.model.MealType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealPollResponse {

    private UUID id;
    private LocalDate pollDate;
    private MealType mealType;
    private MealPollStatus status;
    private UUID dailyMenuId;
    private List<MealPollOptionResponse> options;
    private UUID mySelectedOptionId;
    private List<MealPollMySelectionResponse> mySelections;
    private boolean multiQuantityEnabled;
    private int responseCount;
    private UUID myDeliveryLocationId;
    private String myDeliveryLocationName;
    /** Space timezone id used for pollCloseAt wall clock. */
    private String timezone;
    private LocalDateTime pollCloseAt;
    private LocalDateTime closedAt;
    private LocalDateTime openedAt;
    private MealPollCloseSource closeSource;
}
