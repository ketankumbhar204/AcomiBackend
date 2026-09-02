package com.acomi.acomi_backend.accommodation.api.controller;

import com.acomi.acomi_backend.accommodation.api.dto.request.setup.AccommodationSetupRequest;
import com.acomi.acomi_backend.accommodation.api.dto.request.setup.BuildingAvailabilityRequest;
import com.acomi.acomi_backend.accommodation.api.dto.response.setup.AccommodationSetupPreviewResponse;
import com.acomi.acomi_backend.accommodation.api.dto.response.setup.AccommodationSetupResultResponse;
import com.acomi.acomi_backend.accommodation.api.dto.response.setup.BuildingAvailabilityResponse;
import com.acomi.acomi_backend.accommodation.application.service.AccommodationSetupService;
import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/accommodation/setup")
@RequiredArgsConstructor
@Tag(name = "Accommodation - Quick Setup", description = "Quick setup wizard APIs")
@SecurityRequirement(name = "bearerAuth")
public class AccommodationSetupController {

    private final AccommodationSetupService setupService;

    @PostMapping("/preview")
    @Operation(summary = "Preview quick setup", description = "Computes totals and sample tree without persisting.")
    public ResponseEntity<ApiResponse<AccommodationSetupPreviewResponse>> preview(
            @PathVariable UUID spaceId,
            @RequestBody @Valid AccommodationSetupRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        AccommodationSetupPreviewResponse response = setupService.preview(spaceId, callerId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/building-check")
    @Operation(
            summary = "Check building name availability",
            description = "Returns whether an active building with this name already exists in the space.")
    public ResponseEntity<ApiResponse<BuildingAvailabilityResponse>> checkBuilding(
            @PathVariable UUID spaceId, @RequestBody @Valid BuildingAvailabilityRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(setupService.checkBuildingAvailability(spaceId, callerId, request)));
    }

    @PostMapping
    @Operation(
            summary = "Execute quick setup",
            description = "Creates building structure in a single transaction. Requires Idempotency-Key header.")
    public ResponseEntity<ApiResponse<AccommodationSetupResultResponse>> execute(
            @PathVariable UUID spaceId,
            @RequestBody @Valid AccommodationSetupRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        AccommodationSetupResultResponse response =
                setupService.execute(spaceId, callerId, request, idempotencyKey);
        HttpStatus status = response.isIdempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED;
        String message = response.isIdempotentReplay()
                ? "Setup already completed for this idempotency key"
                : "Accommodation setup completed successfully";
        return ResponseEntity.status(status).body(ApiResponse.success(message, response));
    }
}
