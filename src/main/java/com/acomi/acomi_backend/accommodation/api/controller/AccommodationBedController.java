package com.acomi.acomi_backend.accommodation.api.controller;

import com.acomi.acomi_backend.accommodation.api.dto.response.BedResponse;
import com.acomi.acomi_backend.accommodation.application.service.AccommodationDeletionService;
import com.acomi.acomi_backend.accommodation.application.service.AccommodationRestoreService;
import com.acomi.acomi_backend.accommodation.application.service.BedService;
import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.storage.api.dto.request.AssociateEntityPhotoRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/beds")
@RequiredArgsConstructor
@Tag(name = "Accommodation - Bed lifecycle", description = "Bed detail, deactivate, restore, and permanent delete")
@SecurityRequirement(name = "bearerAuth")
public class AccommodationBedController {

    private final BedService bedService;
    private final AccommodationDeletionService deletionService;
    private final AccommodationRestoreService restoreService;

    @GetMapping("/{bedId}")
    @Operation(summary = "Get bed", description = "Returns bed details with action metadata.")
    public ResponseEntity<ApiResponse<BedResponse>> getBed(
            @PathVariable UUID spaceId, @PathVariable UUID bedId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        BedResponse response = bedService.getBedById(spaceId, bedId, callerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{bedId}/deactivate")
    @Operation(summary = "Deactivate bed", description = "Soft-deletes a bed. OWNER only.")
    public ResponseEntity<Void> deactivateBed(@PathVariable UUID spaceId, @PathVariable UUID bedId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        bedService.deactivateBedById(spaceId, bedId, callerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{bedId}/restore")
    @Operation(summary = "Restore bed", description = "Reactivates a deactivated bed. OWNER only.")
    public ResponseEntity<Void> restoreBed(@PathVariable UUID spaceId, @PathVariable UUID bedId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        restoreService.restoreBed(spaceId, bedId, callerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{bedId}/delete")
    @Operation(
            summary = "Permanently delete bed",
            description = "Hard-deletes a bed. OWNER only. For setup corrections only.")
    public ResponseEntity<Void> deleteBed(@PathVariable UUID spaceId, @PathVariable UUID bedId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        deletionService.deleteBed(spaceId, bedId, callerId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{bedId}/photo")
    @Operation(summary = "Set bed photo", description = "Associates one uploaded photo. Account holder (OWNER) only.")
    public ResponseEntity<ApiResponse<BedResponse>> replacePhoto(
            @PathVariable UUID spaceId,
            @PathVariable UUID bedId,
            @RequestBody @Valid AssociateEntityPhotoRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Bed photo updated",
                bedService.replacePhoto(spaceId, bedId, callerId, request.getFileId())));
    }

    @DeleteMapping("/{bedId}/photo")
    @Operation(summary = "Remove bed photo", description = "Unlinks the bed photo. Account holder (OWNER) only.")
    public ResponseEntity<ApiResponse<BedResponse>> removePhoto(
            @PathVariable UUID spaceId, @PathVariable UUID bedId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                "Bed photo removed", bedService.removePhoto(spaceId, bedId, callerId)));
    }
}
