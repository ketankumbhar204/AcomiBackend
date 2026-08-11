package com.amico.amico_backend.meal.application.service;

import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.common.exception.ResourceNotFoundException;
import com.amico.amico_backend.meal.api.dto.request.UpdateMealPollClosingSettingsRequest;
import com.amico.amico_backend.meal.api.dto.response.MealPollClosingSettingsResponse;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.amico.amico_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MealPollClosingSettingsService {

    private final SpaceRepository spaceRepository;
    private final MealAccessService mealAccessService;

    @Transactional(readOnly = true)
    public MealPollClosingSettingsResponse getSettings(UUID spaceId, UUID callerId) {
        mealAccessService.requireViewMeals(spaceId, callerId);
        return MealPollClosingSettingsResponse.from(loadSpace(spaceId));
    }

    @Transactional
    public MealPollClosingSettingsResponse updateSettings(
            UUID spaceId, UUID callerId, UpdateMealPollClosingSettingsRequest request) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        SpaceEntity space = loadSpace(spaceId);

        try {
            ZoneId.of(request.getTimezone());
        } catch (Exception ex) {
            throw new BusinessException("Invalid timezone: " + request.getTimezone(), HttpStatus.BAD_REQUEST);
        }

        space.setTimezone(request.getTimezone().trim());
        space.setPollCloseBreakfastDayOffset(request.getBreakfastDayOffset());
        space.setPollCloseBreakfastTime(request.getBreakfastTime());
        space.setPollCloseLunchDayOffset(request.getLunchDayOffset());
        space.setPollCloseLunchTime(request.getLunchTime());
        space.setPollCloseDinnerDayOffset(request.getDinnerDayOffset());
        space.setPollCloseDinnerTime(request.getDinnerTime());
        spaceRepository.save(space);

        return MealPollClosingSettingsResponse.from(space);
    }

    private SpaceEntity loadSpace(UUID spaceId) {
        return spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));
    }
}
