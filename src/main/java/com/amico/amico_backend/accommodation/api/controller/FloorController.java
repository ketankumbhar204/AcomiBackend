package com.amico.amico_backend.accommodation.api.controller;

import com.amico.amico_backend.accommodation.api.dto.request.CreateFloorRequest;
import com.amico.amico_backend.accommodation.api.dto.request.CreateUnitRequest;
import com.amico.amico_backend.accommodation.api.dto.request.DuplicateFloorRequest;
import com.amico.amico_backend.accommodation.api.dto.request.UpdateFloorRequest;
import com.amico.amico_backend.accommodation.api.dto.response.DuplicateFloorResponse;
import com.amico.amico_backend.accommodation.api.dto.response.FloorListItemResponse;
import com.amico.amico_backend.accommodation.api.dto.response.FloorResponse;
import com.amico.amico_backend.accommodation.api.dto.response.UnitListItemResponse;
import com.amico.amico_backend.accommodation.api.dto.response.UnitResponse;
import com.amico.amico_backend.accommodation.application.service.UnitService;
import com.amico.amico_backend.accommodation.application.service.AccommodationDeletionService;
import com.amico.amico_backend.accommodation.application.service.AccommodationDuplicateService;
import com.amico.amico_backend.accommodation.application.service.AccommodationLazyListService;
import com.amico.amico_backend.accommodation.application.service.AccommodationRestoreService;
import com.amico.amico_backend.accommodation.application.service.FloorService;
import com.amico.amico_backend.common.security.SecurityUtils;
import com.amico.amico_backend.common.web.ApiResponse;
import com.amico.amico_backend.common.web.PagedResponse;
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
@RequestMapping("/api/v1/spaces/{spaceId}/buildings/{buildingId}/floors")
@RequiredArgsConstructor
@Tag(name = "Accommodation - Floors", description = "Floor structure APIs (PG and Hostel)")
@SecurityRequirement(name = "bearerAuth")
public class FloorController {

    private final FloorService floorService;
    private final UnitService unitService;
    private final AccommodationLazyListService lazyListService;
    private final AccommodationDuplicateService duplicateService;
    private final AccommodationDeletionService deletionService;
    private final AccommodationRestoreService restoreService;

    @PostMapping
    @Operation(summary = "Create floor", description = "Creates a floor. PG and Hostel spaces only.")
    public ResponseEntity<ApiResponse<FloorResponse>> createFloor(
            @PathVariable UUID spaceId,
            @PathVariable UUID buildingId,
            @RequestBody @Valid CreateFloorRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        FloorResponse response = floorService.createFloor(spaceId, buildingId, callerId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Floor created successfully", response));
    }

    @GetMapping
    @Operation(
            summary = "List floors",
            description = "Returns lightweight floor summaries with counts. "
                    + "Use view=full for legacy full FloorResponse list. Supports query and pagination.")
    public ResponseEntity<?> getFloors(
            @PathVariable UUID spaceId,
            @PathVariable UUID buildingId,
            @RequestParam(defaultValue = "summary") String view,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = {"sortOrder", "floorNumber"}) Pageable pageable) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        if ("full".equalsIgnoreCase(view)) {
            List<FloorResponse> floors = floorService.getFloors(spaceId, buildingId, callerId);
            return ResponseEntity.ok(ApiResponse.success(floors));
        }
        PagedResponse<FloorListItemResponse> floors =
                lazyListService.listFloorsByBuilding(spaceId, buildingId, callerId, query, pageable);
        return ResponseEntity.ok(ApiResponse.success(floors));
    }

    @GetMapping("/{floorId}")
    @Operation(summary = "Get floor", description = "Returns floor details.")
    public ResponseEntity<ApiResponse<FloorResponse>> getFloor(
            @PathVariable UUID spaceId, @PathVariable UUID buildingId, @PathVariable UUID floorId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        FloorResponse response = floorService.getFloor(spaceId, buildingId, floorId, callerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{floorId}")
    @Operation(summary = "Update floor", description = "Updates floor details. OWNER or MANAGER only.")
    public ResponseEntity<ApiResponse<FloorResponse>> updateFloor(
            @PathVariable UUID spaceId,
            @PathVariable UUID buildingId,
            @PathVariable UUID floorId,
            @RequestBody @Valid UpdateFloorRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        FloorResponse response = floorService.updateFloor(spaceId, buildingId, floorId, callerId, request);
        return ResponseEntity.ok(ApiResponse.success("Floor updated successfully", response));
    }

    @GetMapping("/{floorId}/units")
    @Operation(
            summary = "List apartments on floor",
            description = "Returns visible apartments on a floor. Use includeSynthetic=true for internal units.")
    public ResponseEntity<?> getUnitsOnFloor(
            @PathVariable UUID spaceId,
            @PathVariable UUID buildingId,
            @PathVariable UUID floorId,
            @RequestParam(defaultValue = "summary") String view,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "false") boolean includeSynthetic,
            @PageableDefault(size = 20, sort = "unitNumber") Pageable pageable) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        if ("full".equalsIgnoreCase(view)) {
            return ResponseEntity.ok(ApiResponse.success(
                    unitService.getUnitsByFloor(spaceId, buildingId, floorId, callerId, includeSynthetic)));
        }
        PagedResponse<UnitListItemResponse> units = lazyListService.listUnitsByFloor(
                spaceId, buildingId, floorId, callerId, query, includeSynthetic, pageable);
        return ResponseEntity.ok(ApiResponse.success(units));
    }

    @PostMapping("/{floorId}/units")
    @Operation(summary = "Create apartment on floor", description = "Apartment PG layout only.")
    public ResponseEntity<ApiResponse<UnitResponse>> createUnitOnFloor(
            @PathVariable UUID spaceId,
            @PathVariable UUID buildingId,
            @PathVariable UUID floorId,
            @RequestBody @Valid CreateUnitRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        UnitResponse response =
                unitService.createUnitUnderFloor(spaceId, buildingId, floorId, callerId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Unit created successfully", response));
    }

    @PostMapping("/{floorId}/duplicate")
    @Operation(summary = "Duplicate floor", description = "Clones a floor with its rooms and beds. OWNER or MANAGER only.")
    public ResponseEntity<ApiResponse<DuplicateFloorResponse>> duplicateFloor(
            @PathVariable UUID spaceId,
            @PathVariable UUID buildingId,
            @PathVariable UUID floorId,
            @RequestBody @Valid DuplicateFloorRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        DuplicateFloorResponse response =
                duplicateService.duplicateFloor(spaceId, buildingId, floorId, callerId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Floor duplicated successfully", response));
    }

    @PostMapping("/{floorId}/deactivate")
    @Operation(
            summary = "Deactivate floor",
            description = "Soft-deletes a floor. OWNER only. Blocked if active rooms exist.")
    public ResponseEntity<Void> deactivateFloor(
            @PathVariable UUID spaceId, @PathVariable UUID buildingId, @PathVariable UUID floorId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        floorService.deactivateFloor(spaceId, buildingId, floorId, callerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{floorId}/restore")
    @Operation(summary = "Restore floor", description = "Reactivates a deactivated floor. OWNER only.")
    public ResponseEntity<Void> restoreFloor(
            @PathVariable UUID spaceId, @PathVariable UUID buildingId, @PathVariable UUID floorId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        restoreService.restoreFloor(spaceId, floorId, callerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{floorId}/delete")
    @Operation(
            summary = "Permanently delete floor",
            description = "Hard-deletes a floor. OWNER only. For setup corrections only.")
    public ResponseEntity<Void> deleteFloor(
            @PathVariable UUID spaceId, @PathVariable UUID buildingId, @PathVariable UUID floorId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        deletionService.deleteFloor(spaceId, floorId, callerId);
        return ResponseEntity.noContent().build();
    }
}
