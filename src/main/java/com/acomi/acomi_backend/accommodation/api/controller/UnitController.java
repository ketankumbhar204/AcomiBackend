package com.acomi.acomi_backend.accommodation.api.controller;

import com.acomi.acomi_backend.accommodation.api.dto.request.BulkCreateUnitsRequest;
import com.acomi.acomi_backend.accommodation.api.dto.request.CreateUnitRequest;
import com.acomi.acomi_backend.accommodation.api.dto.request.UpdateUnitRequest;
import com.acomi.acomi_backend.accommodation.api.dto.response.BulkCreateUnitsResponse;
import com.acomi.acomi_backend.accommodation.api.dto.response.UnitListItemResponse;
import com.acomi.acomi_backend.accommodation.api.dto.response.UnitResponse;
import com.acomi.acomi_backend.accommodation.application.service.AccommodationBulkService;
import com.acomi.acomi_backend.accommodation.application.service.AccommodationLazyListService;
import com.acomi.acomi_backend.accommodation.application.service.AccommodationDeletionService;
import com.acomi.acomi_backend.accommodation.application.service.AccommodationRestoreService;
import com.acomi.acomi_backend.accommodation.application.service.UnitService;
import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.common.web.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
@RequestMapping("/api/v1/spaces/{spaceId}/buildings/{buildingId}/units")
@RequiredArgsConstructor
@Tag(name = "Accommodation - Units", description = "Unit structure APIs (Co-Living and Rental)")
@SecurityRequirement(name = "bearerAuth")
public class UnitController {

    private final UnitService unitService;
    private final AccommodationLazyListService lazyListService;
    private final AccommodationBulkService bulkService;
    private final AccommodationDeletionService deletionService;
    private final AccommodationRestoreService restoreService;

    @PostMapping
    @Operation(summary = "Create unit", description = "Creates a unit. Co-Living and Rental spaces only.")
    public ResponseEntity<ApiResponse<UnitResponse>> createUnit(
            @PathVariable UUID spaceId,
            @PathVariable UUID buildingId,
            @RequestBody @Valid CreateUnitRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        UnitResponse response = unitService.createUnit(spaceId, buildingId, callerId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Unit created successfully", response));
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Bulk create units",
            description = "Creates multiple units in a building. Co-Living and Rental spaces only.")
    public ResponseEntity<ApiResponse<BulkCreateUnitsResponse>> bulkCreateUnits(
            @PathVariable UUID spaceId,
            @PathVariable UUID buildingId,
            @RequestBody @Valid BulkCreateUnitsRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        BulkCreateUnitsResponse response = bulkService.bulkCreateUnits(spaceId, buildingId, callerId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Units created successfully", response));
    }

    @GetMapping
    @Operation(
            summary = "List units",
            description = "Returns lightweight unit summaries with counts. "
                    + "Use view=full for legacy full UnitResponse list. Supports query and pagination.")
    public ResponseEntity<?> getUnits(
            @PathVariable UUID spaceId,
            @PathVariable UUID buildingId,
            @RequestParam(defaultValue = "summary") String view,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "false") boolean includeSynthetic,
            @PageableDefault(size = 20, sort = "unitNumber") Pageable pageable) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        if ("full".equalsIgnoreCase(view)) {
            List<UnitResponse> units = unitService.getUnits(spaceId, buildingId, callerId, includeSynthetic);
            return ResponseEntity.ok(ApiResponse.success(units));
        }
        PagedResponse<UnitListItemResponse> units = lazyListService.listUnitsByBuilding(
                spaceId, buildingId, callerId, query, includeSynthetic, pageable);
        return ResponseEntity.ok(ApiResponse.success(units));
    }

    @GetMapping("/{unitId}")
    @Operation(summary = "Get unit", description = "Returns unit details.")
    public ResponseEntity<ApiResponse<UnitResponse>> getUnit(
            @PathVariable UUID spaceId, @PathVariable UUID buildingId, @PathVariable UUID unitId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        UnitResponse response = unitService.getUnit(spaceId, buildingId, unitId, callerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{unitId}")
    @Operation(summary = "Update unit", description = "Updates unit details. OWNER or MANAGER only.")
    public ResponseEntity<ApiResponse<UnitResponse>> updateUnit(
            @PathVariable UUID spaceId,
            @PathVariable UUID buildingId,
            @PathVariable UUID unitId,
            @RequestBody @Valid UpdateUnitRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        UnitResponse response = unitService.updateUnit(spaceId, buildingId, unitId, callerId, request);
        return ResponseEntity.ok(ApiResponse.success("Unit updated successfully", response));
    }

    @PostMapping("/{unitId}/deactivate")
    @Operation(
            summary = "Deactivate unit",
            description = "Soft-deletes a unit. OWNER only. Blocked if active rooms exist.")
    public ResponseEntity<Void> deactivateUnit(
            @PathVariable UUID spaceId, @PathVariable UUID buildingId, @PathVariable UUID unitId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        unitService.deactivateUnit(spaceId, buildingId, unitId, callerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{unitId}/restore")
    @Operation(summary = "Restore unit", description = "Reactivates a deactivated unit. OWNER only.")
    public ResponseEntity<Void> restoreUnit(
            @PathVariable UUID spaceId, @PathVariable UUID buildingId, @PathVariable UUID unitId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        restoreService.restoreUnit(spaceId, unitId, callerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{unitId}/delete")
    @Operation(
            summary = "Permanently delete unit",
            description = "Hard-deletes a unit. OWNER only. For setup corrections only.")
    public ResponseEntity<Void> deleteUnit(
            @PathVariable UUID spaceId, @PathVariable UUID buildingId, @PathVariable UUID unitId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        deletionService.deleteUnit(spaceId, unitId, callerId);
        return ResponseEntity.noContent().build();
    }
}
