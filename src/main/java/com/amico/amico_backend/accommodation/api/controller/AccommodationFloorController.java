package com.amico.amico_backend.accommodation.api.controller;

import com.amico.amico_backend.accommodation.api.dto.response.FloorResponse;
import com.amico.amico_backend.accommodation.application.service.AccommodationDeletionService;
import com.amico.amico_backend.accommodation.application.service.AccommodationRestoreService;
import com.amico.amico_backend.accommodation.application.service.FloorService;
import com.amico.amico_backend.common.security.SecurityUtils;
import com.amico.amico_backend.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/floors")
@RequiredArgsConstructor
@Tag(name = "Accommodation - Floor lifecycle", description = "Floor detail, deactivate, restore, and permanent delete")
@SecurityRequirement(name = "bearerAuth")
public class AccommodationFloorController {

    private final FloorService floorService;
    private final AccommodationDeletionService deletionService;
    private final AccommodationRestoreService restoreService;

    @GetMapping("/{floorId}")
    @Operation(summary = "Get floor", description = "Returns floor details with action metadata.")
    public ResponseEntity<ApiResponse<FloorResponse>> getFloor(
            @PathVariable UUID spaceId, @PathVariable UUID floorId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        FloorResponse response = floorService.getFloorById(spaceId, floorId, callerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{floorId}/deactivate")
    @Operation(summary = "Deactivate floor", description = "Soft-deletes a floor. OWNER only.")
    public ResponseEntity<Void> deactivateFloor(@PathVariable UUID spaceId, @PathVariable UUID floorId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        floorService.deactivateFloorById(spaceId, floorId, callerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{floorId}/restore")
    @Operation(summary = "Restore floor", description = "Reactivates a deactivated floor. OWNER only.")
    public ResponseEntity<Void> restoreFloor(@PathVariable UUID spaceId, @PathVariable UUID floorId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        restoreService.restoreFloor(spaceId, floorId, callerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{floorId}/delete")
    @Operation(
            summary = "Permanently delete floor",
            description = "Hard-deletes a floor. OWNER only. For setup corrections only.")
    public ResponseEntity<Void> deleteFloor(@PathVariable UUID spaceId, @PathVariable UUID floorId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        deletionService.deleteFloor(spaceId, floorId, callerId);
        return ResponseEntity.noContent().build();
    }
}
