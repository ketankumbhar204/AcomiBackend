package com.amico.amico_backend.meal.application.service;

import com.amico.amico_backend.common.exception.ResourceNotFoundException;
import com.amico.amico_backend.meal.api.dto.request.UpdateMealBillingSettingsRequest;
import com.amico.amico_backend.meal.api.dto.response.MealBillingSettingsResponse;
import com.amico.amico_backend.space.domain.model.MealBillingType;
import com.amico.amico_backend.space.domain.model.PrepaidBalanceUnit;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.amico.amico_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MealBillingSettingsService {

    private final SpaceRepository spaceRepository;
    private final MealAccessService mealAccessService;

    @Transactional(readOnly = true)
    public MealBillingSettingsResponse getSettings(UUID spaceId, UUID callerId) {
        mealAccessService.requireViewMeals(spaceId, callerId);
        return MealBillingSettingsResponse.from(loadSpace(spaceId));
    }

    @Transactional
    public MealBillingSettingsResponse updateSettings(
            UUID spaceId, UUID callerId, UpdateMealBillingSettingsRequest request) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        SpaceEntity space = loadSpace(spaceId);

        MealBillingType billingType = request.getBillingType();
        PrepaidBalanceUnit unit = request.getPrepaidBalanceUnit();
        boolean fallback = request.getFallbackToPayPerMeal() != null
                ? request.getFallbackToPayPerMeal()
                : space.isPrepaidFallbackToPayPerMeal();

        if (billingType == MealBillingType.PREPAID_BALANCE) {
            if (unit == null) {
                unit = space.getPrepaidBalanceUnit() != null
                        ? space.getPrepaidBalanceUnit()
                        : PrepaidBalanceUnit.MEALS;
            }
        } else {
            unit = null;
            fallback = true;
        }

        space.setMealBillingType(billingType);
        space.setPrepaidBalanceUnit(unit);
        space.setPrepaidFallbackToPayPerMeal(fallback);
        spaceRepository.save(space);

        return MealBillingSettingsResponse.from(space);
    }

    private SpaceEntity loadSpace(UUID spaceId) {
        return spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));
    }
}
