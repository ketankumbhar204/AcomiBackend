package com.acomi.acomi_backend.meal.api.controller;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.meal.api.dto.request.CreateMealPlanRequest;
import com.acomi.acomi_backend.meal.api.dto.request.UpdateMealPlanRequest;
import com.acomi.acomi_backend.meal.api.dto.response.MealPlanResponse;
import com.acomi.acomi_backend.meal.application.service.MealPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/meal-plans")
@RequiredArgsConstructor
@Tag(name = "Meal Plans", description = "Space meal plan catalog")
@SecurityRequirement(name = "bearerAuth")
public class MealPlanController {

    private final MealPlanService mealPlanService;

    @GetMapping
    @Operation(summary = "List meal plans")
    public ResponseEntity<ApiResponse<List<MealPlanResponse>>> listPlans(@PathVariable UUID spaceId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Meal plans fetched successfully", mealPlanService.listPlans(spaceId, callerId)));
    }

    @PostMapping
    @Operation(summary = "Create custom meal plan")
    public ResponseEntity<ApiResponse<MealPlanResponse>> createPlan(
            @PathVariable UUID spaceId, @RequestBody @Valid CreateMealPlanRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        MealPlanResponse response = mealPlanService.createCustomPlan(spaceId, callerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Meal plan created successfully", response));
    }

    @PutMapping("/{planId}")
    @Operation(summary = "Update custom meal plan")
    public ResponseEntity<ApiResponse<MealPlanResponse>> updatePlan(
            @PathVariable UUID spaceId,
            @PathVariable UUID planId,
            @RequestBody @Valid UpdateMealPlanRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Meal plan updated successfully",
                mealPlanService.updatePlan(spaceId, planId, callerId, request)));
    }
}
