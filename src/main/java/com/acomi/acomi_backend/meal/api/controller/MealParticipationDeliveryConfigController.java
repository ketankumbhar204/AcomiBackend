package com.acomi.acomi_backend.meal.api.controller;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.meal.api.dto.request.UpdateMealDeliveryConfigRequest;
import com.acomi.acomi_backend.meal.api.dto.response.MealDeliveryConfigResponse;
import com.acomi.acomi_backend.meal.application.service.MealParticipationDeliveryConfigurationService;
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
@RequestMapping("/api/v1/spaces/{spaceId}/members/{memberId}/meal-delivery-config")
@RequiredArgsConstructor
@Tag(name = "Meal Delivery Configuration", description = "Owner/manager delivery location config per member meal type")
@SecurityRequirement(name = "bearerAuth")
public class MealParticipationDeliveryConfigController {

    private final MealParticipationDeliveryConfigurationService configurationService;

    @GetMapping
    public ResponseEntity<ApiResponse<MealDeliveryConfigResponse>> getConfig(
            @PathVariable UUID spaceId, @PathVariable UUID memberId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Meal delivery configuration fetched successfully",
                configurationService.getConfig(spaceId, memberId, callerId)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<MealDeliveryConfigResponse>> replaceConfig(
            @PathVariable UUID spaceId,
            @PathVariable UUID memberId,
            @RequestBody @Valid UpdateMealDeliveryConfigRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Meal delivery configuration saved successfully",
                configurationService.replaceConfig(spaceId, memberId, callerId, request)));
    }
}
