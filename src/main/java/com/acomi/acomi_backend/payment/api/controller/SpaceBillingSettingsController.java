package com.acomi.acomi_backend.payment.api.controller;

import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.payment.api.dto.request.UpdateSpaceBillingSettingsRequest;
import com.acomi.acomi_backend.payment.api.dto.response.SpaceBillingSettingsResponse;
import com.acomi.acomi_backend.payment.application.service.SpaceBillingSettingsService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/v1/spaces/{spaceId}/billing-settings")
@RequiredArgsConstructor
@Tag(name = "Billing Settings", description = "Space-level recurring billing and GST/tax configuration")
@SecurityRequirement(name = "bearerAuth")
public class SpaceBillingSettingsController {

    private final SpaceBillingSettingsService billingSettingsService;

    @GetMapping
    @Operation(summary = "Get space billing and tax settings")
    public ResponseEntity<ApiResponse<SpaceBillingSettingsResponse>> getSettings(
            @PathVariable UUID spaceId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Billing settings fetched successfully",
                billingSettingsService.getSettings(spaceId, callerId)));
    }

    @PutMapping
    @Operation(summary = "Update space billing and tax settings")
    public ResponseEntity<ApiResponse<SpaceBillingSettingsResponse>> updateSettings(
            @PathVariable UUID spaceId, @RequestBody @Valid UpdateSpaceBillingSettingsRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Billing settings updated successfully",
                billingSettingsService.updateSettings(spaceId, callerId, request)));
    }
}
