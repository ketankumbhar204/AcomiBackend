package com.acomi.acomi_backend.meal.api.controller;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.meal.api.dto.request.CreateSubscriptionPlanRequest;
import com.acomi.acomi_backend.meal.api.dto.request.UpdateSubscriptionPlanRequest;
import com.acomi.acomi_backend.meal.api.dto.response.SubscriptionPlanResponse;
import com.acomi.acomi_backend.meal.application.service.SubscriptionPlanService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/subscription-plans")
@RequiredArgsConstructor
@Tag(name = "Subscription Plans", description = "Space subscription plan catalog")
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionPlanController {

    private final SubscriptionPlanService subscriptionPlanService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SubscriptionPlanResponse>>> listPlans(
            @PathVariable UUID spaceId,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        List<SubscriptionPlanResponse> plans = includeInactive
                ? subscriptionPlanService.listAllPlans(spaceId, callerId)
                : subscriptionPlanService.listActivePlans(spaceId, callerId);
        return ResponseEntity.ok(ApiResponse.success("Subscription plans fetched successfully", plans));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SubscriptionPlanResponse>> createPlan(
            @PathVariable UUID spaceId, @RequestBody @Valid CreateSubscriptionPlanRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        SubscriptionPlanResponse response = subscriptionPlanService.createPlan(spaceId, callerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Subscription plan created successfully", response));
    }

    @PutMapping("/{planId}")
    public ResponseEntity<ApiResponse<SubscriptionPlanResponse>> updatePlan(
            @PathVariable UUID spaceId,
            @PathVariable UUID planId,
            @RequestBody @Valid UpdateSubscriptionPlanRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Subscription plan updated successfully",
                subscriptionPlanService.updatePlan(spaceId, planId, callerId, request)));
    }

    @PostMapping("/{planId}/deactivate")
    public ResponseEntity<Void> deactivatePlan(@PathVariable UUID spaceId, @PathVariable UUID planId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        subscriptionPlanService.deactivatePlan(spaceId, planId, callerId);
        return ResponseEntity.noContent().build();
    }
}
