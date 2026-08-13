package com.acomi.acomi_backend.meal.api.controller;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.meal.api.dto.request.UpdateMealPollClosingSettingsRequest;
import com.acomi.acomi_backend.meal.api.dto.response.MealPollClosingSettingsResponse;
import com.acomi.acomi_backend.meal.application.service.MealPollClosingSettingsService;
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
@RequestMapping("/api/v1/spaces/{spaceId}/meal-poll-closing-settings")
@RequiredArgsConstructor
@Tag(name = "Meal Poll Closing Settings", description = "Default auto-close times per meal slot")
@SecurityRequirement(name = "bearerAuth")
public class MealPollClosingSettingsController {

    private final MealPollClosingSettingsService settingsService;

    @GetMapping
    public ResponseEntity<ApiResponse<MealPollClosingSettingsResponse>> get(
            @PathVariable UUID spaceId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Poll closing settings fetched", settingsService.getSettings(spaceId, callerId)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<MealPollClosingSettingsResponse>> update(
            @PathVariable UUID spaceId, @RequestBody @Valid UpdateMealPollClosingSettingsRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Poll closing settings updated", settingsService.updateSettings(spaceId, callerId, request)));
    }
}
