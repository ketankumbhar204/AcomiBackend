package com.acomi.acomi_backend.accommodation.api.controller;

import com.acomi.acomi_backend.accommodation.api.dto.request.CreateBuildingRequest;
import com.acomi.acomi_backend.accommodation.api.dto.request.DuplicateBuildingRequest;
import com.acomi.acomi_backend.accommodation.api.dto.request.UpdateBuildingRequest;
import com.acomi.acomi_backend.accommodation.api.dto.response.BuildingResponse;
import com.acomi.acomi_backend.accommodation.api.dto.response.BuildingSummaryResponse;
import com.acomi.acomi_backend.accommodation.api.dto.response.DuplicateBuildingResponse;
import com.acomi.acomi_backend.accommodation.application.service.AccommodationDeletionService;
import com.acomi.acomi_backend.accommodation.application.service.AccommodationDuplicateService;
import com.acomi.acomi_backend.accommodation.application.service.AccommodationRestoreService;
import com.acomi.acomi_backend.accommodation.application.service.AccommodationSummaryService;
import com.acomi.acomi_backend.accommodation.application.service.BuildingService;
import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
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
@RequestMapping("/api/v1/spaces/{spaceId}/buildings")
@RequiredArgsConstructor
@Tag(name = "Accommodation - Buildings", description = "Building structure APIs")
@SecurityRequirement(name = "bearerAuth")
public class BuildingController {

    private final BuildingService buildingService;
    private final AccommodationSummaryService summaryService;
    private final AccommodationDuplicateService duplicateService;
    private final AccommodationDeletionService deletionService;
    private final AccommodationRestoreService restoreService;

    @PostMapping
    @Operation(summary = "Create building", description = "Creates a building. OWNER or MANAGER only.")
    public ResponseEntity<ApiResponse<BuildingResponse>> createBuilding(
            @PathVariable UUID spaceId,
            @RequestBody @Valid CreateBuildingRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        BuildingResponse response = buildingService.createBuilding(spaceId, callerId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Building created successfully", response));
    }

    @GetMapping
    @Operation(summary = "List buildings", description = "Returns all active buildings in the space.")
    public ResponseEntity<ApiResponse<List<BuildingResponse>>> getBuildings(@PathVariable UUID spaceId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        List<BuildingResponse> buildings = buildingService.getBuildings(spaceId, callerId);
        return ResponseEntity.ok(ApiResponse.success(buildings));
    }

    @GetMapping("/{buildingId}")
    @Operation(summary = "Get building", description = "Returns building details.")
    public ResponseEntity<ApiResponse<BuildingResponse>> getBuilding(
            @PathVariable UUID spaceId, @PathVariable UUID buildingId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        BuildingResponse response = buildingService.getBuilding(spaceId, buildingId, callerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{buildingId}/summary")
    @Operation(
            summary = "Get building summary",
            description = "Returns structure counts and status breakdown for building overview screens. "
                    + "Counts and status fields are flattened in the JSON response.")
    public ResponseEntity<ApiResponse<BuildingSummaryResponse>> getBuildingSummary(
            @PathVariable UUID spaceId, @PathVariable UUID buildingId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        BuildingSummaryResponse response = summaryService.getBuildingSummary(spaceId, buildingId, callerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{buildingId}/duplicate")
    @Operation(
            summary = "Duplicate building",
            description = "Clones a building with its floors/units, rooms, and beds. OWNER or MANAGER only.")
    public ResponseEntity<ApiResponse<DuplicateBuildingResponse>> duplicateBuilding(
            @PathVariable UUID spaceId,
            @PathVariable UUID buildingId,
            @RequestBody @Valid DuplicateBuildingRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        DuplicateBuildingResponse response =
                duplicateService.duplicateBuilding(spaceId, buildingId, callerId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Building duplicated successfully", response));
    }

    @PutMapping("/{buildingId}")
    @Operation(summary = "Update building", description = "Updates building details. OWNER or MANAGER only.")
    public ResponseEntity<ApiResponse<BuildingResponse>> updateBuilding(
            @PathVariable UUID spaceId,
            @PathVariable UUID buildingId,
            @RequestBody @Valid UpdateBuildingRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        BuildingResponse response = buildingService.updateBuilding(spaceId, buildingId, callerId, request);
        return ResponseEntity.ok(ApiResponse.success("Building updated successfully", response));
    }

    @PostMapping("/{buildingId}/deactivate")
    @Operation(
            summary = "Deactivate building",
            description = "Soft-deletes a building. OWNER only. Blocked if active floors or units exist.")
    public ResponseEntity<Void> deactivateBuilding(
            @PathVariable UUID spaceId, @PathVariable UUID buildingId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        buildingService.deactivateBuilding(spaceId, buildingId, callerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{buildingId}/restore")
    @Operation(summary = "Restore building", description = "Reactivates a deactivated building. OWNER only.")
    public ResponseEntity<Void> restoreBuilding(
            @PathVariable UUID spaceId, @PathVariable UUID buildingId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        restoreService.restoreBuilding(spaceId, buildingId, callerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{buildingId}/delete")
    @Operation(
            summary = "Permanently delete building",
            description = "Hard-deletes a building. OWNER only. For setup corrections only.")
    public ResponseEntity<Void> deleteBuilding(
            @PathVariable UUID spaceId, @PathVariable UUID buildingId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        deletionService.deleteBuilding(spaceId, buildingId, callerId);
        return ResponseEntity.noContent().build();
    }
}
