package com.acomi.acomi_backend.accommodation.api.controller;

import com.acomi.acomi_backend.accommodation.api.dto.response.BedSpaceListItemResponse;
import com.acomi.acomi_backend.accommodation.api.dto.response.FloorListItemResponse;
import com.acomi.acomi_backend.accommodation.api.dto.response.RoomListItemResponse;
import com.acomi.acomi_backend.accommodation.api.dto.response.UnitListItemResponse;
import com.acomi.acomi_backend.accommodation.application.service.AccommodationLazyListService;
import com.acomi.acomi_backend.accommodation.domain.model.AccommodationStatus;
import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.common.web.ApiResponse;
import com.acomi.acomi_backend.common.web.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}")
@RequiredArgsConstructor
@Tag(name = "Accommodation - Search", description = "Space-scoped accommodation search for progressive loading")
@SecurityRequirement(name = "bearerAuth")
public class AccommodationSearchController {

    private final AccommodationLazyListService lazyListService;

    @GetMapping("/floors")
    @Operation(
            summary = "Search floors",
            description = "Returns lightweight floor summaries across the space. Supports query and pagination.")
    public ResponseEntity<ApiResponse<PagedResponse<FloorListItemResponse>>> searchFloors(
            @PathVariable UUID spaceId,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = {"sortOrder", "floorNumber"}) Pageable pageable) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        PagedResponse<FloorListItemResponse> floors =
                lazyListService.searchFloorsInSpace(spaceId, callerId, query, pageable);
        return ResponseEntity.ok(ApiResponse.success(floors));
    }

    @GetMapping("/units")
    @Operation(
            summary = "Search units",
            description = "Returns lightweight unit summaries across the space. Supports query and pagination.")
    public ResponseEntity<ApiResponse<PagedResponse<UnitListItemResponse>>> searchUnits(
            @PathVariable UUID spaceId,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = "unitNumber") Pageable pageable) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        PagedResponse<UnitListItemResponse> units =
                lazyListService.searchUnitsInSpace(spaceId, callerId, query, pageable);
        return ResponseEntity.ok(ApiResponse.success(units));
    }

    @GetMapping("/rooms")
    @Operation(
            summary = "Search rooms",
            description = "Returns lightweight room summaries across the space. Supports query and pagination.")
    public ResponseEntity<ApiResponse<PagedResponse<RoomListItemResponse>>> searchRooms(
            @PathVariable UUID spaceId,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = "roomNumber") Pageable pageable) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        PagedResponse<RoomListItemResponse> rooms =
                lazyListService.searchRoomsInSpace(spaceId, callerId, query, pageable);
        return ResponseEntity.ok(ApiResponse.success(rooms));
    }

    @GetMapping("/beds")
    @Operation(
            summary = "Search beds",
            description = "Returns lightweight bed summaries across the space with location context. Supports status filter, query, and pagination.")
    public ResponseEntity<ApiResponse<PagedResponse<BedSpaceListItemResponse>>> searchBeds(
            @PathVariable UUID spaceId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) AccommodationStatus status,
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) UUID floorId,
            @RequestParam(required = false) UUID unitId,
            @PageableDefault(size = 20, sort = "bedNumber") Pageable pageable) {
        UUID callerId = SecurityUtils.getCurrentUserId();
        PagedResponse<BedSpaceListItemResponse> beds =
                lazyListService.searchBedsInSpace(
                        spaceId, callerId, query, status, buildingId, floorId, unitId, pageable);
        return ResponseEntity.ok(ApiResponse.success(beds));
    }
}
