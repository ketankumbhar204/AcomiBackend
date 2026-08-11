package com.amico.amico_backend.meal.application.service;

import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.common.exception.ResourceNotFoundException;
import com.amico.amico_backend.meal.api.dto.request.CreateMealPlanRequest;
import com.amico.amico_backend.meal.api.dto.request.UpdateMealPlanRequest;
import com.amico.amico_backend.meal.api.dto.response.MealPlanResponse;
import com.amico.amico_backend.meal.domain.model.MealPlanCode;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealPlanEntity;
import com.amico.amico_backend.meal.infrastructure.persistence.repository.MealPlanRepository;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.amico.amico_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MealPlanService {

    private static final List<PresetPlan> PRESET_PLANS = List.of(
            new PresetPlan(MealPlanCode.NONE, "No Meals", false, false, false, 0),
            new PresetPlan(MealPlanCode.BREAKFAST, "Breakfast Only", true, false, false, 1),
            new PresetPlan(MealPlanCode.LUNCH, "Lunch Only", false, true, false, 2),
            new PresetPlan(MealPlanCode.DINNER, "Dinner Only", false, false, true, 3),
            new PresetPlan(MealPlanCode.FULL, "Full Meals", true, true, true, 4),
            new PresetPlan(MealPlanCode.CUSTOM, "Custom Plan", false, false, false, 5));

    private final MealPlanRepository mealPlanRepository;
    private final SpaceRepository spaceRepository;
    private final MealAccessService mealAccessService;

    @Transactional
    public List<MealPlanResponse> listPlans(UUID spaceId, UUID callerId) {
        mealAccessService.requireViewMeals(spaceId, callerId);
        ensurePresetPlans(spaceId);
        return mealPlanRepository.findBySpaceIdAndIsActiveTrueOrderBySortOrderAsc(spaceId).stream()
                .map(MealPlanResponse::from)
                .toList();
    }

    @Transactional
    public MealPlanResponse createCustomPlan(UUID spaceId, UUID callerId, CreateMealPlanRequest request) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        SpaceEntity space = loadSpace(spaceId);
        ensurePresetPlans(spaceId);

        MealPlanEntity plan = MealPlanEntity.builder()
                .space(space)
                .code(MealPlanCode.CUSTOM)
                .name(request.getName().trim())
                .breakfastIncluded(Boolean.TRUE.equals(request.getBreakfastIncluded()))
                .lunchIncluded(Boolean.TRUE.equals(request.getLunchIncluded()))
                .dinnerIncluded(Boolean.TRUE.equals(request.getDinnerIncluded()))
                .isActive(true)
                .sortOrder(100)
                .build();
        return MealPlanResponse.from(mealPlanRepository.save(plan));
    }

    @Transactional
    public MealPlanResponse updatePlan(UUID spaceId, UUID planId, UUID callerId, UpdateMealPlanRequest request) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        MealPlanEntity plan = loadPlan(spaceId, planId);
        if (plan.getCode() != MealPlanCode.CUSTOM) {
            throw new BusinessException("Preset meal plans cannot be updated");
        }
        plan.setName(request.getName().trim());
        if (request.getBreakfastIncluded() != null) {
            plan.setBreakfastIncluded(request.getBreakfastIncluded());
        }
        if (request.getLunchIncluded() != null) {
            plan.setLunchIncluded(request.getLunchIncluded());
        }
        if (request.getDinnerIncluded() != null) {
            plan.setDinnerIncluded(request.getDinnerIncluded());
        }
        if (request.getActive() != null) {
            plan.setActive(request.getActive());
        }
        return MealPlanResponse.from(mealPlanRepository.save(plan));
    }

    @Transactional
    public MealPlanEntity ensurePresetPlans(UUID spaceId) {
        SpaceEntity space = loadSpace(spaceId);
        MealPlanEntity fullPlan = null;
        for (PresetPlan preset : PRESET_PLANS) {
            if (mealPlanRepository.existsBySpaceIdAndCode(spaceId, preset.code())) {
                if (preset.code() == MealPlanCode.FULL) {
                    fullPlan = resolvePlanByCode(spaceId, MealPlanCode.FULL);
                }
                continue;
            }
            MealPlanEntity saved = mealPlanRepository.save(MealPlanEntity.builder()
                    .space(space)
                    .code(preset.code())
                    .name(preset.name())
                    .breakfastIncluded(preset.breakfast())
                    .lunchIncluded(preset.lunch())
                    .dinnerIncluded(preset.dinner())
                    .sortOrder(preset.sortOrder())
                    .isActive(true)
                    .build());
            if (preset.code() == MealPlanCode.FULL) {
                fullPlan = saved;
            }
        }
        mealPlanRepository.flush();
        if (fullPlan == null) {
            fullPlan = resolvePlanByCode(spaceId, MealPlanCode.FULL);
        }
        return fullPlan;
    }

    private MealPlanEntity resolvePlanByCode(UUID spaceId, MealPlanCode code) {
        return mealPlanRepository
                .findFirstBySpaceIdAndCodeOrderBySortOrderAsc(spaceId, code)
                .orElseThrow(() -> new ResourceNotFoundException("MealPlan", "code", code));
    }

    public MealPlanEntity loadPlan(UUID spaceId, UUID planId) {
        return mealPlanRepository
                .findByIdAndSpaceId(planId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("MealPlan", "id", planId));
    }

    private SpaceEntity loadSpace(UUID spaceId) {
        return spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));
    }

    private record PresetPlan(
            MealPlanCode code, String name, boolean breakfast, boolean lunch, boolean dinner, int sortOrder) {}
}
