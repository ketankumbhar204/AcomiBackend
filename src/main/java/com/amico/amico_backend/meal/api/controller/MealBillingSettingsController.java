package com.amico.amico_backend.meal.api.controller;

import com.amico.amico_backend.common.security.SecurityUtils;
import com.amico.amico_backend.common.web.ApiResponse;
import com.amico.amico_backend.meal.api.dto.request.UpdateMealBillingSettingsRequest;
import com.amico.amico_backend.meal.api.dto.response.MealBillingSettingsResponse;
import com.amico.amico_backend.meal.application.service.MealBillingSettingsService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/meal-billing-settings")
@RequiredArgsConstructor
@Tag(name = "Meal Billing Settings", description = "Space-level pay-per-meal vs prepaid balance configuration")
@SecurityRequirement(name = "bearerAuth")
public class MealBillingSettingsController {

    private final MealBillingSettingsService billingSettingsService;

    @GetMapping
    public ResponseEntity<ApiResponse<MealBillingSettingsResponse>> getSettings(@PathVariable UUID spaceId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Meal billing settings fetched successfully",
                billingSettingsService.getSettings(spaceId, callerId)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<MealBillingSettingsResponse>> updateSettings(
            @PathVariable UUID spaceId, @RequestBody @Valid UpdateMealBillingSettingsRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Meal billing settings updated successfully",
                billingSettingsService.updateSettings(spaceId, callerId, request)));
    }
}
