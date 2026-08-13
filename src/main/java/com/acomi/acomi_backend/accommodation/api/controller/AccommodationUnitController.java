package com.acomi.acomi_backend.accommodation.api.controller;

import com.acomi.acomi_backend.accommodation.api.dto.request.UpdateUnitRequest;
import com.acomi.acomi_backend.accommodation.api.dto.response.UnitResponse;
import com.acomi.acomi_backend.accommodation.application.service.AccommodationDeletionService;
import com.acomi.acomi_backend.accommodation.application.service.AccommodationRestoreService;
import com.acomi.acomi_backend.accommodation.application.service.UnitService;
import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/units")
@RequiredArgsConstructor
@Tag(name = "Accommodation - Unit lifecycle", description = "Unit detail, deactivate, restore, and permanent delete")
@SecurityRequirement(name = "bearerAuth")
public class AccommodationUnitController {

    private final UnitService unitService;
    private final AccommodationDeletionService deletionService;
    private final AccommodationRestoreService restoreService;

    @GetMapping("/{unitId}")
    @Operation(summary = "Get unit", description = "Returns unit details with action metadata.")
    public ResponseEntity<ApiResponse<UnitResponse>> getUnit(
            @PathVariable UUID spaceId, @PathVariable UUID unitId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        UnitResponse response = unitService.getUnitById(spaceId, unitId, callerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{unitId}")
    @Operation(summary = "Update unit", description = "Updates unit details. OWNER or MANAGER only.")
    public ResponseEntity<ApiResponse<UnitResponse>> updateUnit(
            @PathVariable UUID spaceId,
            @PathVariable UUID unitId,
            @RequestBody @Valid UpdateUnitRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        UnitResponse response = unitService.updateUnitById(spaceId, unitId, callerId, request);
        return ResponseEntity.ok(ApiResponse.success("Unit updated successfully", response));
    }

    @PostMapping("/{unitId}/deactivate")
    @Operation(summary = "Deactivate unit", description = "Soft-deletes a unit. OWNER only.")
    public ResponseEntity<Void> deactivateUnit(@PathVariable UUID spaceId, @PathVariable UUID unitId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        unitService.deactivateUnitById(spaceId, unitId, callerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{unitId}/restore")
    @Operation(summary = "Restore unit", description = "Reactivates a deactivated unit. OWNER only.")
    public ResponseEntity<Void> restoreUnit(@PathVariable UUID spaceId, @PathVariable UUID unitId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        restoreService.restoreUnit(spaceId, unitId, callerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{unitId}/delete")
    @Operation(
            summary = "Permanently delete unit",
            description = "Hard-deletes a unit. OWNER only. For setup corrections only.")
    public ResponseEntity<Void> deleteUnit(@PathVariable UUID spaceId, @PathVariable UUID unitId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        deletionService.deleteUnit(spaceId, unitId, callerId);
        return ResponseEntity.noContent().build();
    }
}
