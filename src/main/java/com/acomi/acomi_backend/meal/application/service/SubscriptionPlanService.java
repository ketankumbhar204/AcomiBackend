package com.acomi.acomi_backend.meal.application.service;

import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.meal.api.dto.request.CreateSubscriptionPlanRequest;
import com.acomi.acomi_backend.meal.api.dto.request.UpdateSubscriptionPlanRequest;
import com.acomi.acomi_backend.meal.api.dto.response.SubscriptionPlanResponse;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.SubscriptionPlanEntity;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.SubscriptionPlanRepository;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionPlanService {

    private final SubscriptionPlanRepository planRepository;
    private final SpaceRepository spaceRepository;
    private final MealAccessService mealAccessService;

    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> listActivePlans(UUID spaceId, UUID callerId) {
        mealAccessService.requireViewMeals(spaceId, callerId);
        return planRepository.findBySpaceIdAndActiveTrueOrderBySortOrderAscNameAsc(spaceId).stream()
                .map(SubscriptionPlanResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> listAllPlans(UUID spaceId, UUID callerId) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        return planRepository.findBySpaceIdOrderBySortOrderAscNameAsc(spaceId).stream()
                .map(SubscriptionPlanResponse::from)
                .toList();
    }

    @Transactional
    public SubscriptionPlanResponse createPlan(UUID spaceId, UUID callerId, CreateSubscriptionPlanRequest request) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        SpaceEntity space = loadSpace(spaceId);
        SubscriptionPlanEntity plan = planRepository.save(SubscriptionPlanEntity.builder()
                .space(space)
                .name(request.getName().trim())
                .mealsIncluded(request.getMealsIncluded())
                .price(request.getPrice())
                .currencyCode(request.getCurrencyCode() != null ? request.getCurrencyCode() : "INR")
                .validityDays(request.getValidityDays())
                .carryForwardUnused(Boolean.TRUE.equals(request.getCarryForwardUnused()))
                .description(request.getDescription())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .active(true)
                .build());
        return SubscriptionPlanResponse.from(plan);
    }

    @Transactional
    public SubscriptionPlanResponse updatePlan(
            UUID spaceId, UUID planId, UUID callerId, UpdateSubscriptionPlanRequest request) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        SubscriptionPlanEntity plan = loadPlan(spaceId, planId);
        plan.setName(request.getName().trim());
        plan.setMealsIncluded(request.getMealsIncluded());
        plan.setPrice(request.getPrice());
        if (request.getCurrencyCode() != null) {
            plan.setCurrencyCode(request.getCurrencyCode());
        }
        plan.setValidityDays(request.getValidityDays());
        if (request.getCarryForwardUnused() != null) {
            plan.setCarryForwardUnused(request.getCarryForwardUnused());
        }
        plan.setDescription(request.getDescription());
        if (request.getSortOrder() != null) {
            plan.setSortOrder(request.getSortOrder());
        }
        if (request.getActive() != null) {
            plan.setActive(request.getActive());
        }
        return SubscriptionPlanResponse.from(planRepository.save(plan));
    }

    @Transactional
    public void deactivatePlan(UUID spaceId, UUID planId, UUID callerId) {
        mealAccessService.requireManageMeals(spaceId, callerId);
        SubscriptionPlanEntity plan = loadPlan(spaceId, planId);
        plan.setActive(false);
        planRepository.save(plan);
    }

    public SubscriptionPlanEntity loadPlan(UUID spaceId, UUID planId) {
        return planRepository
                .findByIdAndSpaceId(planId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionPlan", "id", planId));
    }

    private SpaceEntity loadSpace(UUID spaceId) {
        return spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));
    }
}
