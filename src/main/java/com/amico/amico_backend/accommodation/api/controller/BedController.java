package com.amico.amico_backend.accommodation.api.controller;

import com.amico.amico_backend.accommodation.api.dto.request.BulkCreateBedsRequest;
import com.amico.amico_backend.accommodation.api.dto.request.CreateBedRequest;
import com.amico.amico_backend.accommodation.api.dto.request.UpdateBedRequest;
import com.amico.amico_backend.accommodation.api.dto.response.BedListItemResponse;
import com.amico.amico_backend.accommodation.api.dto.response.BedResponse;
import com.amico.amico_backend.accommodation.api.dto.response.BulkCreateBedsResponse;
import com.amico.amico_backend.accommodation.application.service.AccommodationBulkService;
import com.amico.amico_backend.accommodation.application.service.AccommodationLazyListService;
import com.amico.amico_backend.accommodation.application.service.AccommodationDeletionService;
import com.amico.amico_backend.accommodation.application.service.AccommodationRestoreService;
import com.amico.amico_backend.accommodation.application.service.BedService;
import com.amico.amico_backend.accommodation.domain.model.AccommodationStatus;
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
@RequestMapping("/api/v1/spaces/{spaceId}/rooms/{roomId}/beds")
@RequiredArgsConstructor
@Tag(name = "Accommodation - Beds", description = "Bed structure APIs (PG, Hostel, Co-Living)")
@SecurityRequirement(name = "bearerAuth")
public class BedController {

    private final BedService bedService;
    private final AccommodationLazyListService lazyListService;
    private final AccommodationBulkService bulkService;
    private final AccommodationDeletionService deletionService;
    private final AccommodationRestoreService restoreService;

    @PostMapping
    @Operation(
            summary = "Create bed",
            description = "Creates a bed in a room. Not supported for Rental spaces.")
    public ResponseEntity<ApiResponse<BedResponse>> createBed(
            @PathVariable UUID spaceId,
            @PathVariable UUID roomId,
            @RequestBody @Valid CreateBedRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        BedResponse response = bedService.createBed(spaceId, roomId, callerId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Bed created successfully", response));
    }

    @PostMapping("/bulk")
    @Operation(summary = "Bulk create beds", description = "Creates multiple beds in a room. OWNER or MANAGER only.")
    public ResponseEntity<ApiResponse<BulkCreateBedsResponse>> bulkCreateBeds(
            @PathVariable UUID spaceId,
            @PathVariable UUID roomId,
            @RequestBody @Valid BulkCreateBedsRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        BulkCreateBedsResponse response = bulkService.bulkCreateBeds(spaceId, roomId, callerId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Beds created successfully", response));
    }

    @GetMapping
    @Operation(
            summary = "List beds",
            description = "Returns lightweight bed summaries. "
                    + "Use view=full for legacy full BedResponse list. Supports pagination.")
    public ResponseEntity<?> getBeds(
            @PathVariable UUID spaceId,
            @PathVariable UUID roomId,
            @RequestParam(defaultValue = "summary") String view,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) AccommodationStatus status,
            @PageableDefault(size = 20, sort = "bedNumber") Pageable pageable) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        if ("full".equalsIgnoreCase(view)) {
            List<BedResponse> beds = bedService.getBeds(spaceId, roomId, callerId);
            return ResponseEntity.ok(ApiResponse.success(beds));
        }
        PagedResponse<BedListItemResponse> beds =
                lazyListService.listBedsByRoom(spaceId, roomId, callerId, query, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(beds));
    }

    @GetMapping("/{bedId}")
    @Operation(summary = "Get bed", description = "Returns bed details.")
    public ResponseEntity<ApiResponse<BedResponse>> getBed(
            @PathVariable UUID spaceId, @PathVariable UUID roomId, @PathVariable UUID bedId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        BedResponse response = bedService.getBed(spaceId, roomId, bedId, callerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{bedId}")
    @Operation(summary = "Update bed", description = "Updates bed details. OWNER or MANAGER only.")
    public ResponseEntity<ApiResponse<BedResponse>> updateBed(
            @PathVariable UUID spaceId,
            @PathVariable UUID roomId,
            @PathVariable UUID bedId,
            @RequestBody @Valid UpdateBedRequest request) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        BedResponse response = bedService.updateBed(spaceId, roomId, bedId, callerId, request);
        return ResponseEntity.ok(ApiResponse.success("Bed updated successfully", response));
    }

    @PostMapping("/{bedId}/deactivate")
    @Operation(summary = "Deactivate bed", description = "Soft-deletes a bed. OWNER only.")
    public ResponseEntity<Void> deactivateBed(
            @PathVariable UUID spaceId, @PathVariable UUID roomId, @PathVariable UUID bedId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        bedService.deactivateBed(spaceId, roomId, bedId, callerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{bedId}/restore")
    @Operation(summary = "Restore bed", description = "Reactivates a deactivated bed. OWNER only.")
    public ResponseEntity<Void> restoreBed(
            @PathVariable UUID spaceId, @PathVariable UUID roomId, @PathVariable UUID bedId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        restoreService.restoreBed(spaceId, bedId, callerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{bedId}/delete")
    @Operation(
            summary = "Permanently delete bed",
            description = "Hard-deletes a bed. OWNER only. For setup corrections only.")
    public ResponseEntity<Void> deleteBed(
            @PathVariable UUID spaceId, @PathVariable UUID roomId, @PathVariable UUID bedId) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        deletionService.deleteBed(spaceId, bedId, callerId);
        return ResponseEntity.noContent().build();
    }
}
